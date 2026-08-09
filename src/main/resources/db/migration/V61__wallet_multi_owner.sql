-- =====================================================================
-- V61 (UC-061/062 mở rộng): ví cho Customer, không chỉ Gym.
--
-- Trước đây `wallets` khoá cứng 1-1 với gym_profiles nên khách hàng không có
-- chỗ nhận tiền hoàn: refund chỉ trừ held rồi mất dấu, không có đường tiền ra
-- nào ghi nhận khách đã nhận lại.
--
-- PT KHÔNG có ví: PT làm việc dưới quyền một phòng gym và được gym trả công
-- ngoài nền tảng, nên không có dòng tiền nào của hệ thống chảy vào tay PT.
--
-- Mô hình mới: một ví thuộc về ĐÚNG MỘT chủ sở hữu, xác định bởi owner_type
-- cùng đúng một trong hai khoá ngoại gym_profile_id / user_id. Hai cột riêng
-- (thay vì owner_id đa hình) để giữ được ràng buộc FK ở DB.
--
-- Kèm theo:
--  - banks: master data mã BIN VietQR, cần để sinh QR chuyển khoản cho admin.
--  - bank_accounts: tài khoản thụ hưởng lưu sẵn theo user, chọn khi rút tiền
--    (trước đây phải gõ tay số tài khoản mỗi lần rút -> dễ sai, không đối soát).
--  - withdrawal_requests.ref_code: mã đối soát duy nhất nhúng vào nội dung
--    chuyển khoản, để webhook Casso khớp giao dịch CHI với lệnh rút.
--  - payment_transactions.direction: bảng giao dịch ngân hàng nay ghi cả tiền
--    vào (IN, khớp payment_orders) lẫn tiền ra (OUT, khớp withdrawal_requests).
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. wallets: đa chủ sở hữu
-- ---------------------------------------------------------------------
alter table wallets
    modify gym_profile_id bigint null;

alter table wallets
    add column owner_type varchar(20) not null default 'GYM' after id,
    add column user_id bigint null after gym_profile_id;

-- Toàn bộ ví hiện có đều là ví Gym; default chỉ phục vụ backfill, sau đó bỏ đi
-- để mọi INSERT mới buộc phải ghi rõ owner_type.
update wallets set owner_type = 'GYM' where owner_type is null or owner_type = '';

alter table wallets
    alter column owner_type drop default;

-- UNIQUE cho phép nhiều NULL trên MariaDB nên vẫn dùng được cho khoá tuỳ chọn.
alter table wallets
    add constraint UK_wallets_user unique (user_id);

alter table wallets
    add constraint FK_wallets_user foreign key (user_id) references users (id);

-- Đúng một chủ sở hữu, và khớp với owner_type — chặn ví "mồ côi" hoặc ví bị gán
-- hai chủ nếu một code path mới quên set đủ cột.
alter table wallets
    add constraint chk_wallet_single_owner check (
        (owner_type = 'GYM' and gym_profile_id is not null and user_id is null)
     or (owner_type = 'CUSTOMER' and user_id is not null and gym_profile_id is null)
    );

-- ---------------------------------------------------------------------
-- 2. Sổ cái: loại bút toán mới
--    REFUND_CREDIT — ví khách nhận tiền hoàn từ held của gym.
-- ---------------------------------------------------------------------
alter table wallet_transactions
    modify type enum (
        'HOLD','REFUND','MOVE_TO_PENDING','RELEASE','COMMISSION',
        'FREEZE','UNFREEZE','WITHDRAWAL','DISPUTE_HOLD',
        'REFUND_CREDIT'
    ) not null;

-- ---------------------------------------------------------------------
-- 3. banks: master data mã ngân hàng (BIN dùng cho VietQR)
-- ---------------------------------------------------------------------
create table banks (
    id bigint not null auto_increment,
    bin varchar(10) not null,
    code varchar(20) not null,
    short_name varchar(50) not null,
    name varchar(200) not null,
    active bit not null default 1,
    created_at datetime(6) not null,
    updated_at datetime(6),
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id)
) engine=InnoDB;

alter table banks add constraint UK_banks_bin unique (bin);
alter table banks add constraint UK_banks_code unique (code);

insert into banks (bin, code, short_name, name, active, created_at) values
    ('970436', 'VCB',        'Vietcombank',      'Ngân hàng TMCP Ngoại Thương Việt Nam',                1, now(6)),
    ('970415', 'ICB',        'VietinBank',       'Ngân hàng TMCP Công Thương Việt Nam',                 1, now(6)),
    ('970418', 'BIDV',       'BIDV',             'Ngân hàng TMCP Đầu tư và Phát triển Việt Nam',        1, now(6)),
    ('970405', 'VBA',        'Agribank',         'Ngân hàng NN&PTNT Việt Nam',                          1, now(6)),
    ('970407', 'TCB',        'Techcombank',      'Ngân hàng TMCP Kỹ Thương Việt Nam',                   1, now(6)),
    ('970422', 'MB',         'MB Bank',          'Ngân hàng TMCP Quân đội',                             1, now(6)),
    ('970416', 'ACB',        'ACB',              'Ngân hàng TMCP Á Châu',                               1, now(6)),
    ('970432', 'VPB',        'VPBank',           'Ngân hàng TMCP Việt Nam Thịnh Vượng',                 1, now(6)),
    ('970403', 'STB',        'Sacombank',        'Ngân hàng TMCP Sài Gòn Thương Tín',                   1, now(6)),
    ('970423', 'TPB',        'TPBank',           'Ngân hàng TMCP Tiên Phong',                           1, now(6)),
    ('970443', 'SHB',        'SHB',              'Ngân hàng TMCP Sài Gòn - Hà Nội',                     1, now(6)),
    ('970437', 'HDB',        'HDBank',           'Ngân hàng TMCP Phát triển TP.HCM',                    1, now(6)),
    ('970441', 'VIB',        'VIB',              'Ngân hàng TMCP Quốc tế Việt Nam',                     1, now(6)),
    ('970448', 'OCB',        'OCB',              'Ngân hàng TMCP Phương Đông',                          1, now(6)),
    ('970431', 'EIB',        'Eximbank',         'Ngân hàng TMCP Xuất Nhập khẩu Việt Nam',              1, now(6)),
    ('970426', 'MSB',        'MSB',              'Ngân hàng TMCP Hàng Hải Việt Nam',                    1, now(6)),
    ('970440', 'SEAB',       'SeABank',          'Ngân hàng TMCP Đông Nam Á',                           1, now(6)),
    ('970449', 'LPB',        'LPBank',           'Ngân hàng TMCP Lộc Phát Việt Nam',                    1, now(6)),
    ('970425', 'ABB',        'ABBANK',           'Ngân hàng TMCP An Bình',                              1, now(6)),
    ('970409', 'BAB',        'BacABank',         'Ngân hàng TMCP Bắc Á',                                1, now(6)),
    ('970428', 'NAB',        'NamABank',         'Ngân hàng TMCP Nam Á',                                1, now(6)),
    ('970412', 'PVCB',       'PVcomBank',        'Ngân hàng TMCP Đại Chúng Việt Nam',                   1, now(6)),
    ('970429', 'SCB',        'SCB',              'Ngân hàng TMCP Sài Gòn',                              1, now(6)),
    ('970400', 'SGICB',      'SaigonBank',       'Ngân hàng TMCP Sài Gòn Công Thương',                  1, now(6)),
    ('970433', 'VIETBANK',   'VietBank',         'Ngân hàng TMCP Việt Nam Thương Tín',                  1, now(6)),
    ('546034', 'CAKE',       'CAKE',             'CAKE by VPBank',                                      1, now(6)),
    ('963388', 'TIMO',       'Timo',             'Timo by Ban Viet Bank',                               1, now(6)),
    ('970406', 'DOB',        'DongABank',        'Ngân hàng TMCP Đông Á',                               1, now(6)),
    ('970414', 'OCEANBANK',  'OceanBank',        'Ngân hàng TM TNHH MTV Đại Dương',                     1, now(6)),
    ('970419', 'NCB',        'NCB',              'Ngân hàng TMCP Quốc Dân',                             1, now(6)),
    ('970421', 'VRB',        'VRB',              'Ngân hàng Liên doanh Việt - Nga',                     1, now(6)),
    ('970424', 'SHBVN',      'ShinhanBank',      'Ngân hàng TNHH MTV Shinhan Việt Nam',                 1, now(6)),
    ('970427', 'VAB',        'VietABank',        'Ngân hàng TMCP Việt Á',                               1, now(6)),
    ('970430', 'PGB',        'PGBank',           'Ngân hàng TMCP Thịnh vượng và Phát triển',            1, now(6)),
    ('970438', 'BVB',        'BaoVietBank',      'Ngân hàng TMCP Bảo Việt',                             1, now(6)),
    ('970442', 'HLBVN',      'HongLeong',        'Ngân hàng TNHH MTV Hong Leong Việt Nam',              1, now(6)),
    ('970446', 'COOPBANK',   'COOPBANK',         'Ngân hàng Hợp tác xã Việt Nam',                       1, now(6)),
    ('970452', 'KBHN',       'KookminHN',        'Ngân hàng Kookmin - Chi nhánh Hà Nội',                1, now(6)),
    ('970454', 'VCCB',       'BVBank',           'Ngân hàng TMCP Bản Việt',                             1, now(6)),
    ('970458', 'UOB',        'UnitedOverseas',   'Ngân hàng United Overseas Bank Việt Nam',             1, now(6)),
    ('422589', 'CIMB',       'CIMB',             'Ngân hàng TNHH MTV CIMB Việt Nam',                    1, now(6)),
    ('668888', 'KBank',      'KBank',            'Ngân hàng Đại chúng TNHH Kasikornbank',               1, now(6));

-- ---------------------------------------------------------------------
-- 4. bank_accounts: tài khoản thụ hưởng lưu sẵn của user
-- ---------------------------------------------------------------------
create table bank_accounts (
    id bigint not null auto_increment,
    user_id bigint not null,
    bank_id bigint not null,
    account_number varchar(50) not null,
    account_holder varchar(150) not null,
    is_default bit not null default 0,
    created_at datetime(6) not null,
    updated_at datetime(6),
    created_by varchar(255),
    updated_by varchar(255),
    primary key (id)
) engine=InnoDB;

create index idx_bank_account_user on bank_accounts (user_id);
alter table bank_accounts
    add constraint UK_bank_account_unique unique (user_id, bank_id, account_number);
alter table bank_accounts
    add constraint FK_bank_account_user foreign key (user_id) references users (id),
    add constraint FK_bank_account_bank foreign key (bank_id) references banks (id);

-- ---------------------------------------------------------------------
-- 5. withdrawal_requests: mã đối soát + QR + dấu vết tự khớp
-- ---------------------------------------------------------------------
alter table withdrawal_requests
    add column ref_code varchar(32) null after amount,
    add column bank_bin varchar(10) null after bank_name,
    add column qr_content varchar(500) null after payout_reference,
    add column paid_at datetime(6) null after qr_content,
    add column auto_matched bit not null default 0 after paid_at;

-- Backfill mã đối soát cho lệnh cũ để cột UNIQUE không vướng nhiều bản ghi NULL
-- lẫn lộn khi tra cứu; định dạng trùng với mã BE sinh ra (FMW<id><hex>).
update withdrawal_requests
set ref_code = concat('FMW', id, upper(substr(sha2(concat('legacy-', id), 256), 1, 6)))
where ref_code is null;

alter table withdrawal_requests
    add constraint UK_withdrawal_ref_code unique (ref_code);

-- Lệnh đã PAID trước đây không có mốc chi trả riêng; lấy tạm updated_at để
-- báo cáo theo thời gian không bị rỗng.
update withdrawal_requests
set paid_at = coalesce(updated_at, created_at)
where status = 'PAID' and paid_at is null;

-- ---------------------------------------------------------------------
-- 6. payment_transactions: ghi nhận cả giao dịch CHI từ sao kê Casso
-- ---------------------------------------------------------------------
alter table payment_transactions
    add column direction varchar(10) not null default 'IN' after external_id,
    add column withdrawal_request_id bigint null after payment_order_id;

alter table payment_transactions
    alter column direction drop default;

create index idx_paytxn_withdrawal on payment_transactions (withdrawal_request_id);
alter table payment_transactions
    add constraint FK_paytxn_withdrawal
    foreign key (withdrawal_request_id) references withdrawal_requests (id);

-- ---------------------------------------------------------------------
-- 7. Template thông báo cho sự kiện ví mới (UC-075)
--    Thiếu template thì code vẫn chạy với văn bản mặc định, nhưng admin sẽ
--    không sửa được nội dung — seed để giữ đúng quy ước của V52.
-- ---------------------------------------------------------------------
insert into notification_templates (code, title, body, placeholders, created_at) values
('REFUND_CREDITED_TO_WALLET', 'Đã hoàn {amount} đ vào ví',
 'Tiền hoàn của booking #{bookingId} đã vào ví của bạn — có thể tạo lệnh rút về tài khoản ngân hàng bất cứ lúc nào.',
 '{amount}, {bookingId}', utc_timestamp());
