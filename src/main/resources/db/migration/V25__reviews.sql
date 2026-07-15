-- =====================================================================
-- V25 (UC-069..071): đánh giá, phản hồi và báo cáo/kiểm duyệt review.
-- =====================================================================

create table reviews (
    id bigint not null auto_increment,
    booking_id bigint not null,
    customer_id bigint not null,
    gym_profile_id bigint not null,
    gym_service_id bigint null,
    training_package_id bigint null,
    pt_profile_id bigint null,
    rating int not null,
    comment varchar(2000) null,
    status varchar(20) not null default 'VISIBLE',
    reply varchar(2000) null,
    replied_by varchar(255) null,
    replied_at datetime(6) null,
    created_at datetime(6) not null,
    updated_at datetime(6),
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id),
    unique key uk_reviews_booking (booking_id),
    key idx_reviews_gym (gym_profile_id, status),
    key idx_reviews_pt (pt_profile_id, status),
    key idx_reviews_customer (customer_id),
    constraint fk_reviews_booking foreign key (booking_id) references bookings (id),
    constraint fk_reviews_customer foreign key (customer_id) references users (id),
    constraint fk_reviews_gym foreign key (gym_profile_id) references gym_profiles (id),
    constraint fk_reviews_service foreign key (gym_service_id) references gym_services (id),
    constraint fk_reviews_package foreign key (training_package_id) references training_packages (id),
    constraint fk_reviews_pt foreign key (pt_profile_id) references pt_profiles (id)
) engine = InnoDB;

create table review_reports (
    id bigint not null auto_increment,
    review_id bigint not null,
    reason varchar(500) not null,
    status varchar(20) not null default 'OPEN',
    moderator_note varchar(500) null,
    created_at datetime(6) not null,
    updated_at datetime(6),
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id),
    key idx_review_reports_status (status),
    constraint fk_review_reports_review foreign key (review_id) references reviews (id)
) engine = InnoDB;
