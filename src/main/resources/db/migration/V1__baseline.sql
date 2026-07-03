-- =====================================================================
-- V1: Baseline schema — snapshot của schema do Hibernate (ddl-auto) tạo
-- trước khi chuyển sang quản lý bằng Flyway.
-- DB hiện hữu được baseline tại version 1 (baseline-on-migrate), file này
-- chỉ chạy trên database trống (môi trường mới).
-- =====================================================================

create table audit_logs (
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    updated_at datetime(6),
    action varchar(64) not null,
    target_id varchar(64),
    target_type varchar(64),
    description varchar(1000),
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id)
) engine=InnoDB;

create table favorites (
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    target_id bigint not null,
    updated_at datetime(6),
    user_id bigint not null,
    created_by varchar(255),
    updated_by varchar(255),
    type enum ('GYM','PT') not null,
    primary key (id)
) engine=InnoDB;

create table gym_branches (
    active bit not null,
    created_at datetime(6) not null,
    gym_profile_id bigint not null,
    id bigint not null auto_increment,
    updated_at datetime(6),
    phone varchar(30),
    city varchar(100),
    name varchar(150) not null,
    address varchar(255),
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id)
) engine=InnoDB;

create table gym_documents (
    created_at datetime(6) not null,
    gym_profile_id bigint not null,
    id bigint not null auto_increment,
    updated_at datetime(6),
    document_type varchar(100) not null,
    file_url varchar(500) not null,
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id)
) engine=InnoDB;

create table gym_facilities (
    active bit not null,
    created_at datetime(6) not null,
    gym_profile_id bigint not null,
    id bigint not null auto_increment,
    updated_at datetime(6),
    name varchar(150) not null,
    description varchar(1000),
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id)
) engine=InnoDB;

create table gym_profiles (
    active bit not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    updated_at datetime(6),
    user_id bigint not null,
    phone varchar(30),
    city varchar(100),
    gym_name varchar(150) not null,
    rejection_reason varchar(1000),
    description varchar(2000),
    address varchar(255),
    created_by varchar(255),
    updated_by varchar(255),
    verification_status enum ('APPROVED','NOT_SUBMITTED','PENDING','REJECTED') not null,
    primary key (id)
) engine=InnoDB;

create table gym_services (
    active bit not null,
    duration_minutes integer,
    price decimal(12,2) not null,
    created_at datetime(6) not null,
    gym_profile_id bigint not null,
    id bigint not null auto_increment,
    updated_at datetime(6),
    name varchar(150) not null,
    description varchar(1000),
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id)
) engine=InnoDB;

create table notification_preferences (
    booking_reminders bit not null,
    email_enabled bit not null,
    marketing_enabled bit not null,
    push_enabled bit not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    updated_at datetime(6),
    user_id bigint not null,
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id)
) engine=InnoDB;

create table pt_certifications (
    expiry_date date,
    issue_date date,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    pt_profile_id bigint not null,
    updated_at datetime(6),
    issuing_organization varchar(200),
    name varchar(200) not null,
    credential_url varchar(500),
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id)
) engine=InnoDB;

create table pt_documents (
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    pt_profile_id bigint not null,
    updated_at datetime(6),
    document_type varchar(100) not null,
    file_url varchar(500) not null,
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id)
) engine=InnoDB;

create table pt_profiles (
    active bit not null,
    experience_years integer,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    updated_at datetime(6),
    user_id bigint not null,
    display_name varchar(120) not null,
    rejection_reason varchar(1000),
    bio varchar(2000),
    created_by varchar(255),
    service_area varchar(255),
    specialization varchar(255),
    updated_by varchar(255),
    verification_status enum ('APPROVED','NOT_SUBMITTED','PENDING','REJECTED') not null,
    primary key (id)
) engine=InnoDB;

create table users (
    email_verified bit not null,
    height float(53),
    weight float(53),
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    updated_at datetime(6),
    avatar_url varchar(255),
    created_by varchar(255),
    email varchar(255) not null,
    emergency_contact_name varchar(255),
    emergency_contact_phone varchar(255),
    emergency_contact_relationship varchar(255),
    fitness_equipment_access varchar(255),
    fitness_frequency varchar(255),
    fitness_injuries TEXT,
    fitness_styles TEXT,
    full_name varchar(255),
    location varchar(255),
    main_goal varchar(255),
    password_hash varchar(255) not null,
    phone varchar(255),
    updated_by varchar(255),
    username varchar(255) not null,
    gender enum ('FEMALE','MALE','OTHER'),
    role enum ('ROLE_ADMIN','ROLE_CUSTOMER','ROLE_GYM_OPERATOR','ROLE_PT') not null,
    status enum ('ACTIVE','BANNED','INACTIVE') not null,
    primary key (id)
) engine=InnoDB;

create table verification_tokens (
    used bit not null,
    created_at datetime(6) not null,
    expires_at datetime(6) not null,
    id bigint not null auto_increment,
    updated_at datetime(6),
    user_id bigint not null,
    token varchar(100) not null,
    created_by varchar(255),
    updated_by varchar(255),
    type enum ('EMAIL_VERIFICATION','PASSWORD_RESET') not null,
    primary key (id)
) engine=InnoDB;

create index idx_audit_action on audit_logs (action);

create index idx_audit_target on audit_logs (target_type, target_id);

alter table favorites
    add constraint uk_favorite_user_type_target unique (user_id, type, target_id);

alter table gym_profiles
    add constraint UK87ecdu4i70y9w5jm6v5xwwlj9 unique (user_id);

alter table notification_preferences
    add constraint UKn2jopkbm16qv3xelbvoyjkd0g unique (user_id);

alter table pt_profiles
    add constraint UKiuocodq6o4h2ucp6gbl8u7lb1 unique (user_id);

alter table users
    add constraint UK6dotkott2kjsp8vw4d0m25fb7 unique (email);

alter table users
    add constraint UKr43af9ap4edm43mmtq01oddj6 unique (username);

alter table verification_tokens
    add constraint idx_vtoken_token unique (token);

alter table favorites
    add constraint FKk7du8b8ewipawnnpg76d55fus
    foreign key (user_id) references users (id);

alter table gym_branches
    add constraint FKjgt5bp10ndikgp8jv3o0djt47
    foreign key (gym_profile_id) references gym_profiles (id);

alter table gym_documents
    add constraint FKkqespv647idk4pummo6fic3kj
    foreign key (gym_profile_id) references gym_profiles (id);

alter table gym_facilities
    add constraint FKpxxnl5yhbainsdu0tqyf3bmjx
    foreign key (gym_profile_id) references gym_profiles (id);

alter table gym_profiles
    add constraint FKnmsc4wntxw763f20r916my609
    foreign key (user_id) references users (id);

alter table gym_services
    add constraint FKr6ae3loldmxvscv5ud9actf4t
    foreign key (gym_profile_id) references gym_profiles (id);

alter table notification_preferences
    add constraint FKt9qjvmcl36i14utm5uptyqg84
    foreign key (user_id) references users (id);

alter table pt_certifications
    add constraint FKo6omm4qby1tshiun8oet6nb78
    foreign key (pt_profile_id) references pt_profiles (id);

alter table pt_documents
    add constraint FKav8eod6avyjidaoffsc5cyn76
    foreign key (pt_profile_id) references pt_profiles (id);

alter table pt_profiles
    add constraint FKo7gxxbe0o1bh2m7sffo74g6uh
    foreign key (user_id) references users (id);

alter table verification_tokens
    add constraint FK54y8mqsnq1rtyf581sfmrbp4f
    foreign key (user_id) references users (id);
