# Build Anomaly Service

In the previous part we created a mock customer transaction dataset,
baseline our transaction features, and create customer baseline to be
used in real time transaction scoring.

We trained our anomaly detector model using Isolation Forest algorithm,
test the model, and visualize the customer transaction scores.

In this final part we will cover:

1.  The real-time scoring API.

2.  LLM reasoning for the dashboard.

# Real-Time Scoring API

Before we create scoring API, we will cash customer baseline in Redis
for fast access during transaction real time. We will use [redis om
spring](https://github.com/redis/redis-om-spring)

Document Class

    @Data
    @Builder
    @Document(value = "customer:baseline")
    @NoArgsConstructor
    @AllArgsConstructor
    public class CustomerBaselineDocument {
        @Id
        private String id;                 // "cust:101"
        @Indexed
        private Long customerId;
        private Double meanAmount;
        private Double stdAmount;
        private Double medianAmount;
        private Double segMeanNight;
        private Double segMeanMorning;
        private Double segMeanAfternoon;
        private Double segMeanEvening;

    }

Service Class

    @Slf4j
    @Service
    @RequiredArgsConstructor
    public class CustomerBaselineService {

        private final CustomerBaselineDocumentRepository customerBaselineDocumentRepository;
        private final CustomerBaseLineRepository customerBaseLineRepository;

        @Transactional(readOnly = true)
        public void cashCustomerBaseLine(){
            try (Stream<CustomerBaselineEntity> customerStream = customerBaseLineRepository.findAllBy()) {
                customerStream.forEach(customerBaseLine -> {
                    var customerBaselineDocument = CustomerBaselineDocument.builder()
                            .id("cust:" + customerBaseLine.getId())
                            .customerId(customerBaseLine.getId())
                            .meanAmount(customerBaseLine.getMeanAmount())
                            .stdAmount(customerBaseLine.getStdAmount())
                            .medianAmount(customerBaseLine.getMedianAmount())
                            .segMeanNight(customerBaseLine.getSegMeanNight())
                            .segMeanMorning(customerBaseLine.getSegMeanMorning())
                            .segMeanAfternoon(customerBaseLine.getSegMeanAfternoon())
                            .segMeanEvening(customerBaseLine.getSegMeanEvening()).build();
                    customerBaselineDocumentRepository.save(customerBaselineDocument);
                });
            }
            log.info("customers cached in Redis..");
        }
    }

Loader Class

    @Slf4j
    @Configuration
    @RequiredArgsConstructor
    public class LoadCustomerBaseLineConfig {

        private final CustomerBaselineService customerBaselineDocumentService;

        @Bean
        ApplicationRunner initApplicationRunner() {
            return args -> {
                customerBaselineDocumentService.cashCustomerBaseLine();
            };
        }

    }

Now we have a customer baseline in Redis:

<figure>
<img src="https://github.com/motazco135/anomaly-poc/blob/master/anomaly-api/src/main/resources/images/customer-baseline-redis.png"
alt="Redis Customer Baseline" />
<figcaption aria-hidden="true">Redis Customer Baseline</figcaption>
</figure>

The next Step is to create an online feature scoring service where we
will calculate the feature score based on the transaction real-time data
and customer baseline from redis :

    @Slf4j
    @Service
    @RequiredArgsConstructor
    public class OnlineFeatureService {

        private final CustomerBaselineDocumentRepository customerBaselineDocumentRepository;

        public OnlineFeaturesDto compute(long customerId, double amount, Instant tsUtc) {
            log.info("Computing features for customerId:{} ", customerId);

            var customerBaselineDocument = customerBaselineDocumentRepository.findByCustomerId(String.valueOf(customerId))
                    .orElseThrow(() -> new IllegalStateException("No baseline in Redis for customer " + customerId));
            log.info("Customer baseline document found: {}", customerBaselineDocument);

            double mean = 0, std = 0, median = 0, segNight = 0, segMorning = 0, segAfternoon = 0, segEvening = 0;
            mean = customerBaselineDocument.getMeanAmount();
            std = customerBaselineDocument.getStdAmount();
            median = customerBaselineDocument.getMedianAmount();
            segNight = customerBaselineDocument.getSegMeanNight();
            segMorning = customerBaselineDocument.getSegMeanMorning();
            segAfternoon = customerBaselineDocument.getSegMeanAfternoon();
            segEvening = customerBaselineDocument.getSegMeanEvening();

            int hour = ZonedDateTime.ofInstant(tsUtc, ZoneOffset.UTC).getHour();
            int seg = segmentOfHour(hour);
            double segMean = switch (seg) {
                case 0 -> segNight;
                case 1 -> segMorning;
                case 2 -> segAfternoon;
                default -> segEvening;
            };

            double safeStd  = Math.max(std, 1.0);
            double safeMean = Math.max(mean, 1.0);
            double safeSeg  = Math.max(segMean, 1.0);
            double safeMed  = Math.max(median, 1.0);

            double amountZScore     = (amount - mean) / safeStd;
            double timeSegmentRatio = amount / safeSeg;
            double velocityRatio    = amount / safeMean;
            double medianDeviation  = amount / safeMed;
            double[] realTimeFeatureSet = { amountZScore, timeSegmentRatio, velocityRatio, medianDeviation };

            OnlineFeaturesDto onlineFeaturesDto = OnlineFeaturesDto.builder()
                    .customerId(customerId)
                    .baseLineMean(mean)
                    .baseLineMedian(median)
                    .baseLineStdDeviation(std)
                    .baseLineSegNight(segNight)
                    .baseLineSegMorning(segMorning)
                    .baseLineSegAfternoon(segAfternoon)
                    .baseLineSegEvening(segEvening)
                    .baseLineSegOfHour(seg)
                    .baseLineSegMean(segMean)

                    .amountZScore(amountZScore)
                    .timeSegmentRatio(timeSegmentRatio)
                    .velocityRatio(velocityRatio)
                    .medianDeviation(medianDeviation).build();

            log.info("Customer Online features : {}", onlineFeaturesDto);
            log.info("OnlineFeature compute Completed ...");
            return onlineFeaturesDto;
        }


        /** Simple hour→segment mapping (0 Night, 1 Morning, 2 Afternoon, 3 Evening). */
        private static int segmentOfHour(int h){
            if (h<=5) return 0;
            if (h<=11) return 1;
            if (h<=17) return 2;
            return 3;
        }
    }

Now after we calculate the real-time transaction feature score, We will
create Anomaly Scoring Service where we will load our Isolation Forest
Model and check the transaction score. Based on the configured
Threshold, we will take a decision and return the response.

We have configured the score Threshold as follows:

- Transaction Score &gt;= 0.8500→Decision = Block

- Transaction Score &gt;= 0.7500→Decision = UNDER\_REVIEW

<!-- -->

    @Slf4j
    @Service
    @RequiredArgsConstructor
    public class AnomalyScoringService {

        private final OnlineFeatureService featureService;
        private final ModelRegistryService modelRegistryService;
        private final AnomalyAlertRepository anomalyAlertRepository;
        private final ThresholdService thresholdService;
        private final TransactionRepository transactionRepository;

        @Transactional
        public FundTransferResponseDto score(FundTransferRequestDto fundTransferRequestDto) throws IOException, ClassNotFoundException {
            log.info("---Start Score Fund Transfer Request : {}", fundTransferRequestDto);
            OnlineFeaturesDto realTimeFeatureSet = featureService.compute(fundTransferRequestDto.getCustomerId(), fundTransferRequestDto.getAmount(),
                    fundTransferRequestDto.getTsUtc());
            //scoring
            IsolationForest iForest = modelRegistryService.loadLatestModel();
            double score = iForest.score(new double[]{
                      realTimeFeatureSet.getAmountZScore()
                    , realTimeFeatureSet.getTimeSegmentRatio()
                    , realTimeFeatureSet.getVelocityRatio()
                    , realTimeFeatureSet.getMedianDeviation()
            });

            Decision decision = Decision.ALLOW;
            if (thresholdService.block() != null && score >= thresholdService.block()) {
                decision = Decision.BLOCK;
            } else if (score >= thresholdService.review()) {
                decision = Decision.UNDER_REVIEW;
            }
            log.info("Score Fund Transfer Request decision : {} ,iForest score: {}", decision,score);

            //Save Transaction to T_transaction table
            TransactionEntity transactionEntity = new TransactionEntity();
            transactionEntity.setCustomerId(fundTransferRequestDto.getCustomerId());
            transactionEntity.setCurrencyCode("SAR");
            transactionEntity.setAmount(new BigDecimal(fundTransferRequestDto.getAmount()));
            transactionEntity.setChannel(fundTransferRequestDto.getChannel());
            transactionEntity.setTsUtc(fundTransferRequestDto.getTsUtc());
            final TransactionEntity savedTransaction = transactionRepository.save(transactionEntity);


            // Log only anomalous decisions (UNDER_REVIEW/BLOCK)
            if (decision != Decision.ALLOW) {
                // Log to Transaction Alert Table
                AnomalyAlertEntity anomalyAlertEntity = new AnomalyAlertEntity();
                anomalyAlertEntity.setCustomerId(fundTransferRequestDto.getCustomerId());
                anomalyAlertEntity.setTxnId(savedTransaction.getId().toString());
                anomalyAlertEntity.setAmount(new BigDecimal(fundTransferRequestDto.getAmount()));
                anomalyAlertEntity.setCurrencyCode("SAR");
                anomalyAlertEntity.setChannel(fundTransferRequestDto.getChannel());
                anomalyAlertEntity.setTsUtc(fundTransferRequestDto.getTsUtc());
                anomalyAlertEntity.setScore(new BigDecimal(score));
                anomalyAlertEntity.setSeverity(decision.name());

                String featuresJson = String.format(
                        "{\"amountZScore\": %.6f, \"timeSegmentRatio\": %.6f, \"velocityRatio\": %.6f, \"medianDeviation\": %.6f}",
                        realTimeFeatureSet.getAmountZScore()
                        , realTimeFeatureSet.getTimeSegmentRatio()
                        , realTimeFeatureSet.getVelocityRatio()
                        , realTimeFeatureSet.getMedianDeviation()
                );

                String baselineJson = String.format(
                        "{\"mean\": %.6f, \"std\": %.6f, \"median\": %.6f, \"segmentIndex\":  %.6f, \"segmentMean\": %.6f}",
                        realTimeFeatureSet.getBaseLineMean()
                        , realTimeFeatureSet.getBaseLineStdDeviation()
                        , realTimeFeatureSet.getBaseLineMedian()
                        , realTimeFeatureSet.getBaseLineSegOfHour()
                        , realTimeFeatureSet.getBaseLineSegMean()
                );

                Map<String, Object> factsJson = new HashMap<>();
                factsJson.put("features", featuresJson);
                factsJson.put("baseline", baselineJson);
                anomalyAlertEntity.setFactsJson(factsJson);

                anomalyAlertRepository.save(anomalyAlertEntity);
            }

            log.info("--- Score Fund Transfer Request Completed ....");

            return FundTransferResponseDto.builder()
                    .decision(decision)
                    .score(score)
                    .threshold(thresholdService.review())
                    .onlineFeatures(realTimeFeatureSet)
                    .build();
        }

    }

Now We will create Controller Class:

    @RestController
    @RequestMapping("/api/v1/anomaly")
    @RequiredArgsConstructor
    public class AnomalyController {
        private final AnomalyScoringService scoringService;

        @PostMapping("/score")
        public FundTransferResponseDto score(@RequestBody FundTransferRequestDto req) throws IOException, ClassNotFoundException {
            return scoringService.score(req);
        }
    }

Now Lets Test with CustomerId: 101

    curl -X 'POST' \
      'http://localhost:8084/api/v1/anomaly/score' \
      -H 'accept: */*' \
      -H 'Content-Type: application/json' \
      -d '{
      "customerId": 101,
      "amount": 1000,
      "channel": "ATM",
      "tsUtc": "2025-09-28T21:47:56.205Z"
    }'

Response :

    {
      "decision": "ALLOW",
      "score": 0.5688263188301139,
      "threshold": 0.75,
      "modelId": 0,
      "onlineFeatures": {
        "customerId": 101,
        "amountZScore": -4.019519773709588,
        "timeSegmentRatio": 0.1612392721465768,
        "velocityRatio": 0.15865074941325658,
        "medianDeviation": 0.1594605131917696,
        "baseLineMean": 6303.153333333336,
        "baseLineStdDeviation": 1319.349980069657,
        "baseLineMedian": 6271.145,
        "baseLineSegNight": 6346.901578947367,
        "baseLineSegMorning": 6447.615217391305,
        "baseLineSegAfternoon": 6235.934482758621,
        "baseLineSegEvening": 6201.963000000001,
        "baseLineSegOfHour": 3,
        "baseLineSegMean": 6201.963000000001
      }
    }

As You see from the response above, we receive decision Allow as the
transaction score is lower than the configured Threshold "0.7500".

Let’s Try with a higher amount that is not normal for this customer:
Request:

    curl -X 'POST' \
      'http://localhost:8084/api/v1/anomaly/score' \
      -H 'accept: */*' \
      -H 'Content-Type: application/json' \
      -d '{
      "customerId": 101,
      "amount": 200000,
      "channel": "ATM",
      "tsUtc": "2025-09-28T21:47:56.205Z"
    }'

Response:

    {
      "decision": "BLOCK",
      "score": 0.8622585881819318,
      "threshold": 0.75,
      "modelId": 0,
      "onlineFeatures": {
        "customerId": 101,
        "amountZScore": 146.8123315213452,
        "timeSegmentRatio": 32.24785442931536,
        "velocityRatio": 31.730149882651315,
        "medianDeviation": 31.89210263835392,
        "baseLineMean": 6303.153333333336,
        "baseLineStdDeviation": 1319.349980069657,
        "baseLineMedian": 6271.145,
        "baseLineSegNight": 6346.901578947367,
        "baseLineSegMorning": 6447.615217391305,
        "baseLineSegAfternoon": 6235.934482758621,
        "baseLineSegEvening": 6201.963000000001,
        "baseLineSegOfHour": 3,
        "baseLineSegMean": 6201.963000000001
      }
    }

Now we receive the decision Blocked as the transaction score above the
configured Threshold "0.8500" for block transaction.

# AI Agent for The Dashboard

Now we will Build AI Agent that will do the following :

ExplainAgent → get NEW alerts, calls the LLM, writes
llm\_explanation\_json.

I will use n8n to automate the workflow.

## n8n in docker

to host n8n docker, we will run the following :

    docker run -it --rm \
     --name n8n \
     -p 5678:5678 \
     -e GENERIC_TIMEZONE="Asia/Riyadh" \
     -e TZ="Asia/Riyadh" \
     -e N8N_ENFORCE_SETTINGS_FILE_PERMISSIONS=true \
     -e N8N_RUNNERS_ENABLED=true \
     -v n8n_data:/home/node/.n8n \
     docker.n8n.io/n8nio/n8n

now open the browser <http://localhost:5678/> create an account and
login to start the workflow creation.

## n8n workflow

- We need to add the credentials for openAI api, by accessing the
  Credentials tab also we will add the DB connections details as below :

  <figure>
  <img src="https://github.com/motazco135/anomaly-poc/blob/master/anomaly-api/src/main/resources/images/n8n-1.png" alt="n8n credentials" />
  </figure>

- Now we will start our work flow by adding a Manual Trigger node (Fore
  Demo reason, we should use Trigger)

- Add the Execute Query node to read the data from t\_anomaly\_alert
  table and update the processing status.

  <figure>
  <img src="https://github.com/motazco135/anomaly-poc/blob/master/anomaly-api/src/main/resources/images/n8n-2.png" alt="Query Excution" />
  </figure>

\+ Configure the following query :

\+

    UPDATE public.t_anomaly_alert
       SET agent_status = 'IN_PROGRESS',
           agent_attempts = agent_attempts + 1
     WHERE id IN (
       SELECT id FROM t_anomaly_alert
        WHERE agent_status = 'NEW'
          AND validation_decision IN ('UNDER_REVIEW','BLOCK')
        ORDER BY id
        FOR UPDATE SKIP LOCKED
        LIMIT 50
     )

\+ ![Query Excution](:https://github.com/motazco135/anomaly-poc/blob/master/anomaly-api/src/main/resources/images/n8n-3.png)

- Then Add "IF" node where we will check that the DB query returns a
  result or not, if data found continue workflow.

  <figure>
  <img src="https://github.com/motazco135/anomaly-poc/blob/master/anomaly-api/src/main/resources/images/n8n-4.png" alt="Query Excution" />
  </figure>

- Next we will Add Edit Field(Set) node, where we will map the DB result
  set to specific JSON.

  <figure>
  <img src="https://github.com/motazco135/anomaly-poc/blob/master/anomaly-api/src/main/resources/images/n8n-5.png" alt="Query Excution" />
  </figure>

  <figure>
  <img src="https://github.com/motazco135/anomaly-poc/blob/master/anomaly-api/src/main/resources/images/n8n-6.png" alt="Query Excution" />
  </figure>

- Add an AI Agent node and configure the LLM model and the prompt
  message.

  <figure>
  <img src="https://github.com/motazco135/anomaly-poc/blob/master/anomaly-api/src/main/resources/images/n8n-7.png" alt="Query Excution" />
  </figure>

<figure>
<img src="https://github.com/motazco135/anomaly-poc/blob/master/anomaly-api/src/main/resources/images/n8n-8.png" alt="Query Excution" />
</figure>

\+ As we format the data in the Edit Field node and pass it to the AI
agent node, in the AI agent node we configure the prompt and map the
required dynamic data, we use the following prompt message.

\+

    You are analyzing a banking transaction for anomaly detection.
    anomaly_id : {{ $json.id }}
    txn_id: {{ $json.txn_id }}
    Customer ID: {{ $json.customer_id }}
    Channel: {{ $json.channel }}
    Currency: {{ $json.currency_code }}
    Amount: {{ $json.amount }}
    Anomaly Score: {{ $json.anomaly_score }}
    Threshold p95 : 0.423 , (p98): 0.481, (p99): 0.544
    Decision: {{ $json.validation_decision }}  // ALLOW or UNDER_REVIEW or BLOCK

    Customer Baseline:
    - Mean amount: {{ $json.customer_base_line_mean_amount }}
    - Std deviation: {{ $json.customer_base_std_deviation }}
    - Median amount: {{ $json.customer_base_amount_amount }}
    - Time segment:  {{ $json.customer_base_time_segment }} (0 Night, 1 Morning, 2 Afternoon, 3 Evening)

    Transaction Features:
    - Amount Z-Score: {{ $json.amountZScore }}
    - Time Segment Ratio: {{ $json.time_segment_ratio }}
    - Velocity Ratio: {{ $json.velocity_ratio }}
    - Median Deviation: {{ $json.median_deviation }}

    Return JSON with these exact fields:
    {
      "anomaly id":"anomaly_id",
      "txn_id":"txn_id",
      "customer_id": {{ $json.customer_id }},
      "summary": "Short sentence explaining why score is high or low.",
      "main_factor": "amount_z_score | velocity_ratio | time_segment_ratio | median_deviation",
      "explanation": "2–3 short sentences connecting numbers to reasoning.",
      "risk_category": "LOW | MEDIUM | HIGH | CRITICAL",
      "recommended_action": " UNDER_REVIEW | BLOCK "
    }

    Only suggest 'BLOCK' if the transaction anomaly score is 0.8500 or above  otherwise use 'UNDER_REVIEW'.

- Add another Edit Field node to the process and reformat the Agent node
  output.

- We will need to Alter the alert table to add the summary and main
  factor columns

      ALTER TABLE t_anomaly_alert
        ADD COLUMN llm_summary TEXT,
        ADD COLUMN llm_main_factor VARCHAR(64);

- Finally, Add a query execution node to update the t\_anomaly\_alert
  table.

  <figure>
  <img src="https://github.com/motazco135/anomaly-poc/blob/master/anomaly-api/src/main/resources/images/n8n-8.png" alt="update querye" />
  </figure>

- Final Work flow:

  <figure>
  <img src="https://github.com/motazco135/anomaly-poc/blob/master/anomaly-api/src/main/resources/images/n8n-10.png" alt="update querye" />
  </figure>

when we execute the work flow, you will see the t\_alert table is
updated. we can run the API :

    curl -X 'GET' \
      'http://localhost:8084/api/v1/anomaly/alerts/101' \
      -H 'accept: */*'

Response:

    [
      {
        "txnId": "69905",
        "amount": 500000,
        "currencyCode": "SAR",
        "channel": "ATM",
        "tsUtc": "2025-10-07T22:45:23.296Z",
        "score": 0.8832,
        "severity": "CRITICAL ",
        "factsJson": {
          "baseline": "{\"mean\": 6303.153333, \"std\": 1319.349980, \"median\": 6271.145000, \"segmentIndex\":  3.000000, \"segmentMean\": 6201.963000}",
          "features": "{\"amountZScore\": 374.197032, \"timeSegmentRatio\": 80.619636, \"velocityRatio\": 79.325375, \"medianDeviation\": 79.730257}"
        },
        "agentStatus": "DONE",
        "validationDecision": "BLOCK",
        "explanation": "The SAR 500,000 transaction is over 374 standard deviations above the customer's mean of SAR 6,303, an extraordinary deviation. Other metrics such as velocity ratio (79.33), time segment ratio (80.62), and median deviation (79.73) also show extreme anomalies. The anomaly score of 0.8832 exceeds the 0.8500 threshold, indicating critical risk and necessitating a block. ",
        "summary": "Transaction amount is an extreme outlier compared to customer history, triggering a block. ",
        "mainFactor": "amount_z_score "
      }
    ]

# Limitations & Real-World Handling

It is important to note that not every anomaly is fraud. For example, if
a customer with sufficient funds decides to buy a car in cash, the model
will flag this transaction as an anomaly.

In real banking operations, such transactions are routed to fraud
analysts who review the case, check the customer’s history, and may
contact the customer before allowing or rejecting the transaction. This
human-in-the-loop process ensures that legitimate but rare events do not
harm customer relationships, while still protecting the bank from
fraudulent activity.

# Summary

- Resources :

  - [building-effective-agents](https://www.anthropic.com/engineering/building-effective-agents)

  - [spring-ai-agentic-patterns](https://spring.io/blog/2025/01/21/spring-ai-agentic-patterns)
