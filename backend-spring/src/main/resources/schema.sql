DROP TABLE IF EXISTS audit_log;
DROP TABLE IF EXISTS system_setting;
DROP TABLE IF EXISTS college_application;
DROP TABLE IF EXISTS evaluation_record;
DROP TABLE IF EXISTS guidance_record;
DROP TABLE IF EXISTS message_notice;
DROP TABLE IF EXISTS form_instance;
DROP TABLE IF EXISTS form_template;
DROP TABLE IF EXISTS internship_application;
DROP TABLE IF EXISTS mentor_application;
DROP TABLE IF EXISTS organization;
DROP TABLE IF EXISTS student;
DROP TABLE IF EXISTS teacher;
DROP TABLE IF EXISTS user_account;
DROP TABLE IF EXISTS college;

CREATE TABLE college (
    id VARCHAR(64) PRIMARY KEY,
    school_name VARCHAR(128),
    name VARCHAR(128),
    contact_name VARCHAR(64),
    contact_phone VARCHAR(32),
    description VARCHAR(255)
);

CREATE TABLE user_account (
    id VARCHAR(64) PRIMARY KEY,
    account VARCHAR(64) NOT NULL,
    name VARCHAR(64) NOT NULL,
    role VARCHAR(32) NOT NULL,
    password VARCHAR(128) NOT NULL,
    must_change_password BOOLEAN,
    status VARCHAR(32) NOT NULL,
    college_id VARCHAR(64),
    last_login_at TIMESTAMP
);

CREATE TABLE teacher (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    name VARCHAR(64) NOT NULL,
    employee_no VARCHAR(64) NOT NULL,
    college_id VARCHAR(64) NOT NULL,
    department VARCHAR(128),
    phone VARCHAR(32),
    status VARCHAR(32)
);

CREATE TABLE student (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    name VARCHAR(64) NOT NULL,
    student_no VARCHAR(64) NOT NULL,
    college_id VARCHAR(64) NOT NULL,
    major VARCHAR(128),
    class_name VARCHAR(128),
    phone VARCHAR(32),
    internship_type VARCHAR(32),
    internship_status VARCHAR(32),
    profile_completed BOOLEAN
);

CREATE TABLE organization (
    id VARCHAR(64) PRIMARY KEY,
    college_id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    address VARCHAR(255),
    contact_name VARCHAR(64),
    contact_phone VARCHAR(32),
    nature VARCHAR(64),
    cooperation_status VARCHAR(64)
);

CREATE TABLE mentor_application (
    id VARCHAR(64) PRIMARY KEY,
    student_id VARCHAR(64) NOT NULL,
    teacher_id VARCHAR(64) NOT NULL,
    status VARCHAR(64) NOT NULL,
    student_remark VARCHAR(255),
    teacher_remark VARCHAR(255),
    college_remark VARCHAR(255),
    created_at TIMESTAMP,
    teacher_reviewed_at TIMESTAMP,
    college_reviewed_at TIMESTAMP,
    effective_at TIMESTAMP
);

CREATE TABLE internship_application (
    id VARCHAR(64) PRIMARY KEY,
    student_id VARCHAR(64) NOT NULL,
    organization_id VARCHAR(64) NOT NULL,
    status VARCHAR(64) NOT NULL,
    batch_name VARCHAR(64),
    position VARCHAR(64),
    grade_target VARCHAR(64),
    start_date DATE,
    end_date DATE,
    remark VARCHAR(255),
    attachments_json CLOB,
    organization_confirmation VARCHAR(64),
    organization_feedback VARCHAR(255),
    received_at DATE,
    created_at TIMESTAMP,
    reviewed_at TIMESTAMP,
    review_comment VARCHAR(255)
);

CREATE TABLE form_template (
    code VARCHAR(64) PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    category VARCHAR(32) NOT NULL,
    description VARCHAR(255),
    applicable_types_json CLOB,
    field_schema_json CLOB,
    enabled BOOLEAN DEFAULT TRUE,
    sort_no INT DEFAULT 100,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE form_instance (
    id VARCHAR(64) PRIMARY KEY,
    student_id VARCHAR(64) NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    template_name VARCHAR(64) NOT NULL,
    category VARCHAR(32) NOT NULL,
    status VARCHAR(64) NOT NULL,
    version_no INT,
    content_json CLOB,
    attachments_json CLOB,
    teacher_comment VARCHAR(255),
    college_comment VARCHAR(255),
    score INT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    submitted_at TIMESTAMP,
    teacher_reviewed_at TIMESTAMP,
    college_reviewed_at TIMESTAMP,
    history_json CLOB
);

CREATE TABLE message_notice (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    type VARCHAR(64) NOT NULL,
    title VARCHAR(128) NOT NULL,
    content VARCHAR(255),
    link VARCHAR(255),
    read_flag BOOLEAN,
    created_at TIMESTAMP
);

CREATE TABLE guidance_record (
    id VARCHAR(64) PRIMARY KEY,
    teacher_id VARCHAR(64) NOT NULL,
    student_id VARCHAR(64) NOT NULL,
    guidance_at TIMESTAMP,
    mode VARCHAR(32),
    problem VARCHAR(255),
    advice VARCHAR(255),
    follow_up VARCHAR(255)
);

CREATE TABLE evaluation_record (
    id VARCHAR(64) PRIMARY KEY,
    teacher_id VARCHAR(64) NOT NULL,
    student_id VARCHAR(64) NOT NULL,
    stage_comment VARCHAR(255),
    summary_comment VARCHAR(255),
    final_score INT,
    dimension_scores_json CLOB,
    strengths_comment VARCHAR(255),
    improvement_comment VARCHAR(255),
    college_comment VARCHAR(255),
    college_score INT,
    submitted_to_college BOOLEAN,
    confirmed_by_college BOOLEAN,
    evaluated_at TIMESTAMP,
    college_confirmed_at TIMESTAMP
);

CREATE TABLE college_application (
    id VARCHAR(64) PRIMARY KEY,
    school_name VARCHAR(128),
    college_name VARCHAR(128),
    contact_name VARCHAR(64),
    contact_phone VARCHAR(32),
    description VARCHAR(255),
    status VARCHAR(64),
    review_comment VARCHAR(255),
    created_at TIMESTAMP
);

CREATE TABLE audit_log (
    id VARCHAR(64) PRIMARY KEY,
    type VARCHAR(64),
    operator_id VARCHAR(64),
    action VARCHAR(128),
    detail VARCHAR(255),
    created_at TIMESTAMP
);

CREATE TABLE system_setting (
    setting_key VARCHAR(64) PRIMARY KEY,
    setting_value VARCHAR(64) NOT NULL,
    category VARCHAR(64),
    name VARCHAR(128) NOT NULL,
    description VARCHAR(255),
    updated_at TIMESTAMP
);
