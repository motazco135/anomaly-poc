CREATE SEQUENCE transactions_id_seq
    INCREMENT BY 100
    START WITH 1
    cache 100
    OWNED BY t_transactions.id;

CREATE SEQUENCE anomaly_alert_id_seq
    INCREMENT BY 100
    START WITH 1
    cache 100
    OWNED BY t_anomaly_alert.id;

CREATE SEQUENCE model_registry_id_seq
    INCREMENT BY 100
    START WITH 1
    cache 100
    OWNED BY t_model_registry.model_id;