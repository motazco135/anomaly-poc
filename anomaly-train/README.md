In our previous article [Anomaly
Detection](https://www.linkedin.com/pulse/anomaly-detection-motaz-mohammed-sameh-vymkf)
we understood what is anomaly detection? , How Isolation Forest
algorithm works? Also, We have created the application architecture and
addressed the challenges related to How we can build the anomaly model.

In this part,we’re going to generate a fake dataset that simulates
real-world customer transactions data. This data will help us train a
model to predict whether a customer transaction is an anomaly or not
based on the anomaly score.

# DataBase Schema

    -- 1) Customer Transaction Table
    CREATE TABLE IF NOT EXISTS t_transactions (
      id            BIGSERIAL PRIMARY KEY,
      customer_id   BIGINT NOT NULL,
      amount        NUMERIC(18,2) NOT NULL,
      currency_code VARCHAR(10) NOT NULL,
      channel       VARCHAR(16) NOT NULL,     -- POS | ATM | ONLINE | WIRE ..
      ts_utc        TIMESTAMP NOT NULL
    );
    CREATE INDEX IF NOT EXISTS idx_trx_cust_ts ON t_transactions(customer_id, ts_utc);

    -- 2) Model registry Table (stores serialized Isolation Forest)
    CREATE TABLE IF NOT EXISTS t_model_registry (
      model_id         BIGSERIAL PRIMARY KEY,
      created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
      trees            INT NOT NULL,
      subsample        INT NOT NULL,
      feature_schema   TEXT NOT NULL,         -- e.g. "[amount_z,time_segment_ratio,velocity_ratio,median_dev]"
      schema_hash      VARCHAR(64) NOT NULL,  -- e.g. SHA-256 of feature_schema
      trained_rows     BIGINT NOT NULL,
      notes            TEXT,
      model_bytes      BYTEA NOT NULL
    );

    -- 3) Anomaly alert table
    CREATE TABLE IF NOT EXISTS t_anomaly_alert (
      id            BIGSERIAL PRIMARY KEY,
      txn_id        VARCHAR(64) UNIQUE,
      customer_id   BIGINT NOT NULL,
      amount    NUMERIC(18,2) NOT NULL,
      currency_code VARCHAR(10) NOT NULL,
      channel       VARCHAR(16) NOT NULL,
      ts_utc        TIMESTAMP NOT NULL,
      score         NUMERIC(6,4) NOT NULL,
      severity      VARCHAR(8) NOT NULL,
      facts_json    JSONB NOT NULL,
      created_at    TIMESTAMP NOT NULL DEFAULT NOW()
    );

# Train the Model

We will train the model on the data from the t\_transactions table for
each transaction we will follow features:

- amount\_z = (amount − mean\_overall) / std\_overall

- time\_segment\_ratio = amount / mean\_in\_this\_time\_segment

  - segments:

    - 0=Night(00–05)

    - 1=Morning(06–11)

    - 2=Afternoon(12–17)

    - 3=Evening(18–23)

- velocity\_ratio = amount / mean\_overall

- median\_dev = amount / median\_overall

We will create a new table to store computed features for each
transaction, This table will be used also in training the model and
another table to have the customer baseline this table will be used in
realtime transaction scoring and store data in redis.

    CREATE TABLE IF NOT EXISTS t_transaction_features (
      id                    BIGSERIAL PRIMARY KEY,
      txn_id                BIGINT UNIQUE REFERENCES t_transactions(id) ON DELETE CASCADE,
      customer_id           BIGINT NOT NULL,
      ts_utc                TIMESTAMP NOT NULL,
      amount                DOUBLE PRECISION NOT NULL,
      currency_code         VARCHAR(10) NOT NULL,

      -- 4 features (fixed order)
      amount_z_score        DOUBLE PRECISION NOT NULL,
      time_segment_ratio    DOUBLE PRECISION NOT NULL,
      velocity_ratio        DOUBLE PRECISION NOT NULL,
      median_deviation      DOUBLE PRECISION NOT NULL,

      -- snapshot of the PRIOR baseline used to compute features
      baseline_n            BIGINT NOT NULL,             -- number of prior txns
      baseline_mean_amount  DOUBLE PRECISION NOT NULL,
      baseline_std_amount   DOUBLE PRECISION NOT NULL,
      baseline_median_amount DOUBLE PRECISION NOT NULL,
      baseline_seg_index    INT NOT NULL,           -- 0..3 (Night, Morning, Afternoon, Evening)
      baseline_seg_mean     DOUBLE PRECISION NOT NULL,

      created_at            TIMESTAMP NOT NULL DEFAULT now()
    );
    CREATE INDEX IF NOT EXISTS idx_tf_cust_ts ON t_transaction_features(customer_id, ts_utc);

    CREATE SEQUENCE transaction_features_id_seq
        INCREMENT BY 100
        START WITH 1
        cache 100
        OWNED BY t_transaction_features.id;

    -- customer base line
    CREATE TABLE IF NOT EXISTS t_customer_baseline_90d (
      customer_id            BIGINT PRIMARY KEY,
      n_tx                   BIGINT NOT NULL,
      mean_amount            DOUBLE PRECISION NOT NULL,
      std_amount             DOUBLE PRECISION NOT NULL,
      median_amount          DOUBLE PRECISION NOT NULL,
      seg_mean_night         DOUBLE PRECISION NOT NULL,
      seg_mean_morning       DOUBLE PRECISION NOT NULL,
      seg_mean_afternoon     DOUBLE PRECISION NOT NULL,
      seg_mean_evening       DOUBLE PRECISION NOT NULL,
      updated_at             TIMESTAMP NOT NULL DEFAULT now()
    );

## Data Preparation

Now we will generate the Dataset that will be used for training the
Model, we will assume that we have 500 customers for each customer we
will generate 120 transactions which represent the last 90-day
transaction, now will have 60K transactions to train the model on

    @Slf4j
    @Component
    @RequiredArgsConstructor
    public class DataPreparationService {

        private final TransactionRepository transactionRepository;

        Random rnd = new Random(42);
        int customers = 500;
        int txnsPer = 120; // ~90 days
        String[] channels = {"POS","ATM","ONLINE","WIRE"};

        public void prepareData() {
            LocalDate start = LocalDate.now().minusDays(90);
            List<TransactionEntity> transactionEntityList = new ArrayList<TransactionEntity>();
            for (int c=1;c<=customers;c++) {
                double mean = 3000 + rnd.nextDouble()*4000;  // 3k..7k
                double std  = 600 + rnd.nextDouble()*1000;   // 600..1600
                for (int i=0;i<txnsPer;i++) {
                    LocalDateTime ts = start.plusDays(rnd.nextInt(90))
                            .atTime(rnd.nextInt(24), rnd.nextInt(60));
                    double amt = Math.max(50, mean + rnd.nextGaussian()*std);
                    // inject 5% spikes at night
                    if (rnd.nextDouble()<0.05 && ts.getHour()<6) {
                        amt *= (5 + rnd.nextDouble()*8); // 5x..13x
                    }
                    TransactionEntity transactionEntity = new TransactionEntity();
                    transactionEntity.setCustomerId(Long.valueOf(c));
                    transactionEntity.setAmount(BigDecimal.valueOf(Math.round(amt*100.0)/100.0));
                    transactionEntity.setCurrencyCode("SAR");
                    transactionEntity.setChannel(channels[rnd.nextInt(channels.length)]);
                    transactionEntity.setTsUtc(Timestamp.valueOf(ts).toInstant());
                    transactionEntityList.add(transactionEntity);
                }
            }
            transactionRepository.saveAll(transactionEntityList);
        }
    }

## Model Training

Here we will use the data that we prepared to train the model, We will
use JAVA [Smile](https://haifengl.github.io/) library.

We will see how we will calculate a feature store that will be used in
the training of the model as we agree we will have the following
features that capture deviations from a customer’s normal spending
behavior:

- The Z-score shows how unusual the transaction amount is.

- The time segment ratio captures whether the timing is unusual.

- Velocity ratio captures whether the customer is transacting faster
  than usual.

- Median deviation highlights distance from their typical transaction
  size.

So let’s start first by creating the feature baseline that will be used
to train the model:

    /**
     * For each customer get the transaction list for each transaction compute
     * features and save it in the t_transaction_feature table
    * */
    @Slf4j
    @Component
    @RequiredArgsConstructor
    public class AnomalyFeatureFillService {

        private final TransactionRepository transactionRepository;
        private final TransactionFeatureRepository transactionFeatureRepository;
        private final CustomerBaseLineRepository customerBaseLineRepository;

        private static final int TIME_SEGMENTS = 4; // 0 Night 00–05, 1 Morning 06–11, 2 Afternoons 12–17, 3 Evenings 18–23
        private static final double EPS = 1.0;

        public List<Long> getCustomerIds(){
            List<Long> customerIds = new ArrayList<>();
            for (int i = 1; i <=500 ; i++) {
                customerIds.add(Long.valueOf(i));
            }
            return customerIds;
        }

        public List<TransactionEntity> getTransactionsByCustomerId(Long customerId){
            List<TransactionEntity> transactions = transactionRepository.findAllByCustomerIdOrderByTsUtcAsc(customerId);
            log.info("Found {} transactions , for customerId: {} ", transactions.size(),customerId);
            return transactions;
        }

        public void doFeatureFill(){
            Map<Long, FeatureBaseline> state = new HashMap<>();
            List<Long> customerIds = getCustomerIds();
            for (int i = 0; i < customerIds.size(); i++) {
                Long customerId = customerIds.get(i);
                CustomerBaseLine customerBaseLine = new CustomerBaseLine();
                customerBaseLine.customerId = customerId;
                //TODO: Pagination is missing
                //get Customer transactions
                List<TransactionEntity> transactions = getTransactionsByCustomerId(customerId);
                List<TransactionFeatureEntity> transactionFeatureEntityList = new ArrayList<>();
                for (int j = 0; j < transactions.size(); j++) {
                    TransactionEntity transaction = transactions.get(j);

                    // Convert the Instant to LocalDateTime
                    LocalDateTime localDateTime = LocalDateTime.ofInstant(transaction.getTsUtc(), ZoneOffset.UTC);
                    int hour        = localDateTime.atZone(ZoneOffset.UTC).getHour();
                    int seg         = segmentOfHour(hour);
                    log.info("Transaction id :{},CustomerID:{} , Segment {}", transaction.getId(), customerId, seg);

                    FeatureBaseline  featureBaseline = state.computeIfAbsent(transaction.getCustomerId(),k->new FeatureBaseline());
                    // ----- PRIOR baseline values (before including current txn) -----
                    double safeMean = max(featureBaseline.mean, EPS);
                    double safeStd  = max(featureBaseline.std, EPS);
                    double segMean  = featureBaseline.segCount[seg] > 0 ? featureBaseline.segMean[seg] : safeMean;
                    double safeSegMean = max(segMean, EPS);
                    double priorMedian = max(computeMedian(featureBaseline.allAmounts), EPS);

                    // 4 features
                    double amountZScore     = (transaction.getAmount().doubleValue() - featureBaseline.mean) / safeStd;
                    double timeSegmentRatio = transaction.getAmount().doubleValue()  / safeSegMean;
                    double velocityRatio    = transaction.getAmount().doubleValue()  / safeMean;     // your definition
                    double medianDeviation  = transaction.getAmount().doubleValue()  / priorMedian;
                    log.info("Computed Features: amountZScore:{}, timeSegmentRatio:{}, velocityRatio:{}, medianDeviation:{}",
                            amountZScore, timeSegmentRatio, velocityRatio, medianDeviation);

                    // insert t_transaction_feature
                    TransactionFeatureEntity transactionFeatureEntity = new TransactionFeatureEntity();
                    transactionFeatureEntity.setTxn(transaction);
                    transactionFeatureEntity.setCustomerId(customerId);
                    transactionFeatureEntity.setTsUtc(transaction.getTsUtc());
                    transactionFeatureEntity.setAmountZScore(amountZScore);
                    transactionFeatureEntity.setAmount(transaction.getAmount().doubleValue());
                    transactionFeatureEntity.setCurrencyCode(transaction.getCurrencyCode());
                    transactionFeatureEntity.setTimeSegmentRatio(timeSegmentRatio);
                    transactionFeatureEntity.setVelocityRatio(velocityRatio);
                    transactionFeatureEntity.setMedianDeviation(medianDeviation);

                    transactionFeatureEntity.setBaselineN(featureBaseline.n);
                    transactionFeatureEntity.setBaselineMeanAmount(featureBaseline.mean);
                    transactionFeatureEntity.setBaselineStdAmount(featureBaseline.std);
                    transactionFeatureEntity.setBaselineMedianAmount(priorMedian);
                    transactionFeatureEntity.setBaselineSegIndex(seg);
                    transactionFeatureEntity.setBaselineSegMean(segMean);
                    transactionFeatureEntity.setIsTrainable((featureBaseline.n >= 10) && (featureBaseline.std >= 1.0));
                    transactionFeatureEntityList.add(transactionFeatureEntity);

                    //------ UPDATE Customer BaseLine
                    customerBaseLine.allAmounts.add(transaction.getAmount().doubleValue());
                    switch (seg) {
                        case 0 -> customerBaseLine.nightAmounts.add(transaction.getAmount().doubleValue());
                        case 1 -> customerBaseLine.morningAmounts.add(transaction.getAmount().doubleValue());
                        case 2 -> customerBaseLine.afternoonAmounts.add(transaction.getAmount().doubleValue());
                        default -> customerBaseLine.eveningAmounts.add(transaction.getAmount().doubleValue());
                    }

                    // Welford for mean/std
                    customerBaseLine.priorCount += 1;
                    double customerDelta = transaction.getAmount().doubleValue() - customerBaseLine.priorMean;
                    customerBaseLine.priorMean += customerDelta / customerBaseLine.priorCount;
                    double customerDelta2 = transaction.getAmount().doubleValue() - customerBaseLine.priorMean;
                    customerBaseLine.priorM2 += customerDelta * customerDelta2;
                    customerBaseLine.priorStd = (customerBaseLine.priorCount > 1) ? max(Math.sqrt(customerBaseLine.priorM2 / (customerBaseLine.priorCount - 1)), 1.0) : customerBaseLine.priorStd;
                    // online segment mean
                    customerBaseLine.segCount[seg] += 1;
                    double customerPrev = customerBaseLine.segMean[seg];
                    customerBaseLine.segMean[seg] = customerPrev + (transaction.getAmount().doubleValue() - customerPrev) / customerBaseLine.segCount[seg];

                    // ----- UPDATE Feature baseline with current txn (Welford + seg means + amounts list) -----
                    featureBaseline.allAmounts.add(transaction.getAmount().doubleValue());
                    featureBaseline.n += 1;
                    double delta  = transaction.getAmount().doubleValue() - featureBaseline.mean;
                    featureBaseline.mean += delta / featureBaseline.n;
                    double delta2 = transaction.getAmount().doubleValue() - featureBaseline.mean;
                    featureBaseline.m2 += delta * delta2;
                    featureBaseline.std = (featureBaseline.n > 1) ? sqrt(featureBaseline.m2 / (featureBaseline.n - 1)) : max(featureBaseline.std, EPS);
                    featureBaseline.segCount[seg] += 1;
                    double prev = featureBaseline.segMean[seg];
                    featureBaseline.segMean[seg] = prev + (transaction.getAmount().doubleValue() - prev) / featureBaseline.segCount[seg];
                    featureBaseline.print();
                }
                saveCustomerBaseline(customerBaseLine);
                transactionFeatureRepository.saveAll(transactionFeatureEntityList);
            }
        }

        private void saveCustomerBaseline(CustomerBaseLine customerBaseLine){
            if (!customerBaseLine.allAmounts.isEmpty()) {
                CustomerBaselineEntity customerBaselineEntity = new CustomerBaselineEntity();
                double meanAll   = mean(customerBaseLine.allAmounts);
                double stdAll    = stdAround(meanAll, customerBaseLine.allAmounts);
                double medianAll = median(customerBaseLine.allAmounts);

                double segNightMean     = customerBaseLine.nightAmounts.isEmpty()     ? meanAll : mean(customerBaseLine.nightAmounts);
                double segMorningMean   = customerBaseLine.morningAmounts.isEmpty()   ? meanAll : mean(customerBaseLine.morningAmounts);
                double segAfternoonMean = customerBaseLine.afternoonAmounts.isEmpty() ? meanAll : mean(customerBaseLine.afternoonAmounts);
                double segEveningMean   = customerBaseLine.eveningAmounts.isEmpty()   ? meanAll : mean(customerBaseLine.eveningAmounts);

                customerBaselineEntity.setId(customerBaseLine.customerId);
                customerBaselineEntity.setNTx(Long.valueOf(customerBaseLine.allAmounts.size()));
                customerBaselineEntity.setMeanAmount(meanAll);
                customerBaselineEntity.setMedianAmount(medianAll);
                customerBaselineEntity.setStdAmount(stdAll);
                customerBaselineEntity.setSegMeanNight(segNightMean);
                customerBaselineEntity.setSegMeanMorning(segMorningMean);
                customerBaselineEntity.setSegMeanAfternoon(segAfternoonMean);
                customerBaselineEntity.setSegMeanEvening(segEveningMean);
                customerBaseLineRepository.save(customerBaselineEntity);
            }
        }

        private static int segmentOfHour(int hour) {
            if (hour <= 5)  return 0; // Night
            if (hour <= 11) return 1; // Morning
            if (hour <= 17) return 2; // Afternoon
            return 3;                 // Evening
        }
        private static double computeMedian(List<Double> amounts) {
            if (amounts.isEmpty()) return 1.0;
            ArrayList<Double> copy = new ArrayList<>(amounts);
            Collections.sort(copy);
            int n = copy.size();
            return (n % 2 == 1) ? copy.get(n/2) : 0.5 * (copy.get(n/2 - 1) + copy.get(n/2));
        }


        private static double median(List<Double> xs) {
            if (xs.isEmpty()) return 1.0;
            ArrayList<Double> copy = new ArrayList<>(xs);
            Collections.sort(copy);
            int n = copy.size();
            return n % 2 == 1 ? copy.get(n / 2) : 0.5 * (copy.get(n / 2 - 1) + copy.get(n / 2));
        }

        private static double mean(List<Double> xs) {
            if (xs.isEmpty()) return 0.0;
            double s = 0.0; for (double v : xs) s += v; return s / xs.size();
        }

        private static double stdAround(double mean, List<Double> xs) {
            if (xs.size() <= 1) return 1.0;
            double s2 = 0.0; for (double v : xs) { double d = v - mean; s2 += d * d; }
            return max(Math.sqrt(s2 / (xs.size() - 1)), 1.0);
        }

    }

The code above in summary will do the following:

<figure>
<img src="https://github.com/motazco135/anomaly-model-training/blob/master/src/main/resources/data-flow.png" alt="Data Flow" />
<figcaption aria-hidden="true">Data Flow</figcaption>
</figure>

We calculated the transaction features and also baseline customer.

Now we can train the model and will save the model to our DB (We can
also save the model in the filesystem and only store the metadata in the
DB).

This enables us to have multiple versions from the model.

    @Slf4j
    @Component
    @RequiredArgsConstructor
    public class TrainIsolationForestService {

        private final TransactionFeatureRepository transactionFeatureRepository;
        private final ModelRegistryRepository modelRegistryRepository;

        private static final int TREES = 150;
        private static final int SUBSAMPLE = 256;

        public void trainModel(){
            //TODO: Use Pagination
            List<double[]> rows = new ArrayList<>();
            transactionFeatureRepository.findByIsTrainable(true).forEach(transactionFeature ->{
                    rows.add(new double[]{
                            transactionFeature.getAmountZScore(),
                            transactionFeature.getTimeSegmentRatio(),
                            transactionFeature.getVelocityRatio(),
                            transactionFeature.getMedianDeviation()
                    });
            });

            if(!rows.isEmpty()){
                double[][] trainingData = rows.toArray(new double[0][]);
                log.info("Training Isolation Forest model...");

                //Compute sampling_rate = min(1.0, TARGET_SUBSAMPLE / n)
                double samplingRate = Math.min(1.0, SUBSAMPLE / (double) trainingData.length);
                log.info("sampling rate: {}",  samplingRate);
                IsolationForest.Options options = new IsolationForest.Options(TREES, 0, samplingRate, 0);
                IsolationForest iforest = IsolationForest.fit(trainingData,options);
                log.info("Model trained successfully.");

                // serialize + save
                byte[] bytes;
                try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                     ObjectOutputStream oos = new ObjectOutputStream(bos)) {
                    oos.writeObject(iforest);
                    oos.flush();
                    bytes = bos.toByteArray();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                //Save model
                String schema ="[amountZScore,timeSegmentRatio,velocityRatio,medianDeviation]";
                ModelRegistryEntity modelRegistryEntity = new ModelRegistryEntity();
                modelRegistryEntity.setTrees(iforest.trees().length);
                modelRegistryEntity.setSubsample((int) samplingRate);
                modelRegistryEntity.setFeatureSchema(schema);
                modelRegistryEntity.setSchemaHash(Integer.toHexString(schema.hashCode()));
                modelRegistryEntity.setTrainedRows(Long.valueOf(trainingData.length));
                modelRegistryEntity.setNotes("Isolation Forest trained from transaction_features");
                modelRegistryEntity.setModelBytes(bytes);
                modelRegistryRepository.save(modelRegistryEntity);

                // Calculate anomaly scores for all points
                double[] scores = new double[trainingData.length];
                for (int i = 0; i < trainingData.length; i++) {
                    scores[i] = iforest.score(trainingData[i]);
                }

                Arrays.sort(scores);
                double p95 = scores[(int)Math.floor(0.95 * (scores.length - 1))]; // 95th percentile
                double p98 = scores[(int)Math.floor(0.98 * (scores.length - 1))]; // 98th percentile
                double p99 = scores[(int)Math.floor(0.99 * (scores.length - 1))]; // 99th percentile
                log.info("Calibrated percentiles (higher=worse): p95={}, p98={}, p99={}", p95, p98, p99);

                // Display results
                log.info("Dataset size: {} pints", trainingData.length);
                log.info("Number of trees: {} ", iforest.trees().length);
                log.info("Subsample size: {}", iforest.getExtensionLevel());

            }

        }
    }

In the above code we train the model in the feature store we have
created in our DB.

You will see that we calculated "Calibrated percentiles" this helped us
to identify the threshold that we will use to compare the anomaly score.

Our calibration:

- p95 ≈ 0.423 → top 5% of transactions

- p98 ≈ 0.481 → top 2%

- p99 ≈ 0.544 → top 1% (strictest)

Based on the above calibration:

- If we want ~5% of transactions flagged → use threshold = 0.423.

- If you want ~1% flagged (only the most extreme) → use threshold =
  0.544.

## Real Time Transaction Scoring

Now we will run a real-time transaction scoring to test the model
behavior

    @Slf4j
    @Service
    @RequiredArgsConstructor
    public class RealTimeTransactionTestService {

        private static final double THRESHOLD = 0.55;
        private final ModelRegistryRepository modelRegistryRepository;
        private final CustomerBaseLineRepository customerBaseLineRepository;
        private final TransactionRepository transactionRepository;
        private final TransactionFeatureRepository transactionFeatureRepository;
        private final AnomalyAlertRepository  anomalyAlertRepository;

        private IsolationForest loadLatestModel() throws IOException, ClassNotFoundException {
            IsolationForest iForest = null ;
            Optional<ModelRegistryEntity> optionalModelRegistryEntity = modelRegistryRepository.findLatestIFModel();
            if (optionalModelRegistryEntity.isPresent()) {
                ModelRegistryEntity modelRegistryEntity = optionalModelRegistryEntity.get();
                ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(modelRegistryEntity.getModelBytes()));
                iForest  = (IsolationForest) ois.readObject();
                ois.close();
            }
            return iForest;
        }

        /** Simple hour→segment mapping (0 Night, 1 Morning, 2 Afternoon, 3 Evening). */
        private static int segmentOfHour(int hour) {
            if (hour <= 5)  return 0;
            if (hour <= 11) return 1;
            if (hour <= 17) return 2;
            return 3;
        }

        public void simulateAndScore(long customerId,
                                     double amount,
                                     LocalDateTime tsUtc) throws Exception {
            //Load model
            IsolationForest iForest = loadLatestModel();

            double sTypical  = iForest.score(new double[]{0, 1, 1, 1});
            double sExtreme  = iForest.score(new double[]{10, 20, 20, 20});
            boolean lowerIsWorse = sExtreme < sTypical;
            log.info("IF orientation: typical={} extreme={} → lowerIsWorse={}", sTypical, sExtreme, lowerIsWorse);

            //get customer baseline
            double mean = 0, std = 0, median = 0, segNight = 0, segMorning = 0, segAfternoon = 0, segEvening = 0;
            Optional<CustomerBaselineEntity> customerBaselineEntityOption = customerBaseLineRepository.findById(customerId);
            if (!customerBaselineEntityOption.isPresent()) {
               throw  new Exception("Customer not found");
            }

            CustomerBaselineEntity customerBaselineEntity = customerBaselineEntityOption.get();
            mean = customerBaselineEntity.getMeanAmount();
            std = customerBaselineEntity.getStdAmount();
            median = customerBaselineEntity.getMedianAmount();
            segNight = customerBaselineEntity.getSegMeanNight();
            segMorning = customerBaselineEntity.getSegMeanMorning();
            segAfternoon = customerBaselineEntity.getSegMeanAfternoon();
            segEvening = customerBaselineEntity.getSegMeanEvening();

            //Calculate
            int seg = segmentOfHour(tsUtc.getHour());
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

            //Scoring
            double score = iForest.score(realTimeFeatureSet);
            boolean underReview = score >= THRESHOLD;

            log.info(" custId={} seg={} amount={}  features=[z={} timeSeg={} velocityRatio={} medDev={}] score={} decision={}"
                    , customerId, seg, String.format("%.2f", amount),
                    String.format("%.3f", amountZScore),
                    String.format("%.3f", timeSegmentRatio),
                    String.format("%.3f", velocityRatio),
                    String.format("%.3f", medianDeviation),
                    String.format("%.4f", score),
                    underReview ? "UNDER_REVIEW" : "ALLOW");
        }

    }

When we run the above code with the following input:

    long customerId = 101L;
    double amountSar = 500000;
    LocalDateTime tsUtc = LocalDateTime.now().withHour(2).withMinute(30).withSecond(0).withNano(0);

We will see that the transaction is marked as "**UNDER\_REVIEW**" (check
the log)and the transaction will be logged in the t\_anomaly\_alert
table.

    custId=101 seg=0 amount=500000.00  features=[z=374.197 timeSeg=78.779 velocityRatio=79.325 medDev=79.730] score=0.8768 decision=UNDER_REVIEW

And when we run with the following input for the same customer:

    long customerId = 101L;
    double amountSar = 1000;
    LocalDateTime tsUtc = LocalDateTime.now().withHour(2).withMinute(30).withSecond(0).withNano(0);

The result will show "**ALLOW**"

    custId=101 seg=0 amount=1000.00  features=[z=-4.020 timeSeg=0.158 velocityRatio=0.159 medDev=0.159] score=0.5892 decision=ALLOW

# Visual Analysis of Customer ID 101

1.  Histogram – Amount Z-Score

    <figure>
    <img src="https://github.com/motazco135/anomaly-model-training/blob/master/src/main/resources/hist_amount_z.png"
    alt="Histogram – Amount Z-Score" />
    <figcaption aria-hidden="true">Histogram – Amount Z-Score</figcaption>
    </figure>

    - Most transactions for this customer have Z-scores close to 0,
      meaning their amounts are very typical compared to the customer’s
      historical spending.

    - A few transactions show higher Z-scores, indicating amounts that
      deviate from the average — potential candidates for anomaly
      detection.

2.  Histogram – Isolation Forest Scores

    <figure>
    <img src="https://github.com/motazco135/anomaly-model-training/blob/master/src/main/resources/hist_anomaly_score.png"
    alt="Isolation Forest Scores" />
    <figcaption aria-hidden="true">Isolation Forest Scores</figcaption>
    </figure>

    - The majority of scores cluster around 0.33–0.36, which Isolation
      Forest interprets as normal behavior.

    - Only a handful of transactions are pushed toward 0.45–0.48,
      showing that they are more isolated and unusual compared to the
      rest.

3.  Histogram – Median Deviation

    <figure>
    <img src="https://github.com/motazco135/anomaly-model-training/blob/master/src/main/resources/hist_median_deviation.png"
    alt="Median Deviation" />
    <figcaption aria-hidden="true">Median Deviation</figcaption>
    </figure>

    - The distribution is centered around 0.9–1.1, meaning most
      transactions are very close to the customer’s median behavior.

    - Outliers above 1.4 signal that some transactions deviate
      significantly from the expected middle point.

4.  Histogram – Time Segment Ratio

    <figure>
    <img src="https://github.com/motazco135/anomaly-model-training/blob/master/src/main/resources/hist_time_segment_ratio.png"
    alt="Time Segment Ratio" />
    <figcaption aria-hidden="true">Time Segment Ratio</figcaption>
    </figure>

    - Transactions mostly fall between 0.9–1.1, showing that this
      customer usually transacts at consistent times of the day.

    - Ratios above 1.3–1.5 highlight transactions occurring at unusual
      times, potentially adding to anomaly signals.

5.  Histogram – Velocity Ratio

    <figure>
    <img src="https://github.com/motazco135/anomaly-model-training/blob/master/src/main/resources/hist_velocity_ratio.png"
    alt="Velocity Ratio" />
    <figcaption aria-hidden="true">Velocity Ratio</figcaption>
    </figure>

    - The velocity ratio also clusters around 0.9–1.1, meaning
      transaction frequency is stable.

    - A few values above 1.3 suggest short bursts of activity that are
      less typical for this customer.

6.  Scatter Plot – Amount Z-Score vs. Velocity Ratio (Colored by IF
    Score)

    <figure>
    <img src="https://github.com/motazco135/anomaly-model-training/blob/master/src/main/resources/scatter_z_vs_velocity_by_score.png"
    alt="../resources/ai/part4/scatter_z_vs_velocity_by_score.png" />
    </figure>

    - Most points align diagonally near the origin, confirming stable
      and correlated spending + transaction velocity.

    - A few points are visibly farther from the cluster, with higher
      anomaly scores, indicating where Isolation Forest detects true
      outliers.

For **Customer ID = 101**, the feature histograms clearly show that most
transactions fall within stable and expected ranges. Isolation Forest
assigns low anomaly scores
(<sub>0.33)\ for\ the\ majority\ of\ points,\ while\ only\ a\ few\ transactions\ stand\ out\ with\ higher\ scores\ (</sub>0.45+).
These outliers are associated with unusually high Z-scores or deviations
in transaction velocity.

The scatter plot of Z-score vs. Velocity Ratio confirms this visually:
while most transactions cluster tightly along the normal behavior line,
a few outliers break away, highlighting the model’s ability to isolate
anomalies without needing explicit fraud labels.

# Summary

In this Part we created a mock customer transaction dataset, baseline
our transaction features and create customer baseline to be used in real
time transaction scoring.

We trained our anomaly detector model using Isolation Forest algorithm,
test the model and visualize the customer transaction scores.

You can find the complete code in
[github](https://github.com/motazco135/anomaly-poc/tree/master/anomaly-train)

In the Next final Part we will build a transaction service, Where we
will score the transaction in real time to decide if it is an outlier or
not.

