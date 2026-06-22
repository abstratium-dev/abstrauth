-- Hibernate Envers custom revision info table.
-- Stores revision metadata for all audited entity changes.
CREATE TABLE REVINFO (
    REV BIGINT AUTO_INCREMENT PRIMARY KEY,
    REVTSTMP BIGINT,
    username VARCHAR(255),
    correlation_id VARCHAR(255),
    change_note VARCHAR(255)
);

CREATE INDEX I_revinfo_timestamp ON REVINFO(REVTSTMP);
CREATE INDEX I_revinfo_username ON REVINFO(username);
