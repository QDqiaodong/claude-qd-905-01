-- 玻璃深加工厂 · 原片与工序流水管理
-- 库名 glass_works；application.yml 里 ddl-auto: none，表结构全部由本文件在 MySQL 镜像构建期导入。
-- 关键：镜像构建期跑 initdb 时 mysql 客户端默认 latin1，会把 UTF-8 中文存成双编码，
--       因此每个 .sql 文件的第一句必须是 SET NAMES utf8mb4。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET character_set_client = utf8mb4;
SET character_set_connection = utf8mb4;
SET character_set_results = utf8mb4;

CREATE DATABASE IF NOT EXISTS glass_works
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE glass_works;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS rework_event;
DROP TABLE IF EXISTS rework_batch;
DROP TABLE IF EXISTS finished_rack;
DROP TABLE IF EXISTS inspection_record;
DROP TABLE IF EXISTS deep_process_record;
DROP TABLE IF EXISTS tempering_batch;
DROP TABLE IF EXISTS edge_grinding;
DROP TABLE IF EXISTS cutting_piece;
DROP TABLE IF EXISTS cutting_order;
DROP TABLE IF EXISTS glass_stock;
DROP TABLE IF EXISTS storage_location;
SET FOREIGN_KEY_CHECKS = 1;

-- ---------------- 库位（区 / 架 / 层） ----------------
CREATE TABLE storage_location (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    zone_code VARCHAR(8) NOT NULL,
    zone_name VARCHAR(32),
    rack_code VARCHAR(16) NOT NULL,
    level_no INT NOT NULL,
    batch_count INT DEFAULT 0,
    sheet_count INT DEFAULT 0,
    max_load_kg DECIMAL(12,2) DEFAULT 0,
    current_load_kg DECIMAL(12,2) DEFAULT 0,
    note VARCHAR(64),
    UNIQUE KEY uk_location (zone_code, rack_code, level_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 原片批次 ----------------
-- spec_text 是派生列：实体用 @Access(PROPERTY) 由 厚度+长×宽 拼出，读回时反解成三个字段。
CREATE TABLE glass_stock (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_no VARCHAR(32) NOT NULL UNIQUE,
    glass_type VARCHAR(32) NOT NULL,
    thickness_mm INT,
    length_mm INT,
    width_mm INT,
    sheet_count INT,
    supplier VARCHAR(64),
    transmittance DECIMAL(5,2),
    inbound_date DATE,
    location_id BIGINT,
    weight_kg DECIMAL(12,2),
    spec_text VARCHAR(48),
    KEY idx_stock_type (glass_type),
    KEY idx_stock_thickness (thickness_mm)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 切割单 ----------------
-- utilization_rate 是派生列：由 出片面积合计 / 原片面积 得出并做 0~100% 边界校验。
CREATE TABLE cutting_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(32) NOT NULL UNIQUE,
    stock_id BIGINT,
    batch_no VARCHAR(32),
    glass_type VARCHAR(32),
    layout_no VARCHAR(32),
    output_count INT,
    source_area_sqm DECIMAL(12,3),
    output_area_sqm DECIMAL(12,3),
    remnant_area_sqm DECIMAL(12,3),
    remnant_spec VARCHAR(48),
    cutter VARCHAR(48),
    operator VARCHAR(32),
    status VARCHAR(16),
    created_at DATETIME,
    utilization_rate DECIMAL(6,2)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 切割出片规格明细 ----------------
-- area_sqm 是派生列：长 × 宽 × 数量 / 1,000,000。
CREATE TABLE cutting_piece (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cutting_order_id BIGINT NOT NULL,
    sort_no INT,
    length_mm INT,
    width_mm INT,
    quantity INT,
    area_sqm DECIMAL(12,4),
    remark VARCHAR(64),
    KEY idx_piece_order (cutting_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 磨边记录 ----------------
CREATE TABLE edge_grinding (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_no VARCHAR(32) NOT NULL UNIQUE,
    cutting_order_id BIGINT,
    batch_no VARCHAR(32),
    machine VARCHAR(48),
    edge_type VARCHAR(16),
    wheel_model VARCHAR(32),
    feed_speed DECIMAL(5,2),
    piece_count INT,
    chip_count INT,
    chip_note VARCHAR(128),
    operator VARCHAR(32),
    grind_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 钢化炉批次 ----------------
-- temp_range 是派生列：由 temp_low / temp_high 拼成 “660-700”，读回时反拆。
CREATE TABLE tempering_batch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_no VARCHAR(32) NOT NULL UNIQUE,
    furnace_no VARCHAR(16),
    glass_type VARCHAR(32),
    thickness_mm INT,
    temp_low INT,
    temp_high INT,
    temp_range VARCHAR(24),
    heat_temp INT,
    heat_seconds INT,
    quench_pressure DECIMAL(5,2),
    warpage DECIMAL(5,3),
    particle_count INT,
    output_count INT,
    broken_count INT,
    stage VARCHAR(16),
    operator VARCHAR(32),
    created_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 深加工工序记录（镀膜 / 中空 / 夹胶） ----------------
CREATE TABLE deep_process_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_no VARCHAR(32) NOT NULL UNIQUE,
    process_type VARCHAR(16) NOT NULL,
    batch_no VARCHAR(32),
    glass_type VARCHAR(32),
    thickness_mm INT,
    piece_count INT,
    film_system VARCHAR(32),
    target_material VARCHAR(32),
    film_thickness_nm INT,
    emissivity DECIMAL(5,3),
    spacer VARCHAR(32),
    gas_type VARCHAR(16),
    dew_point INT,
    pvb_thickness DECIMAL(4,2),
    autoclave_temp INT,
    autoclave_pressure DECIMAL(4,2),
    autoclave_minutes INT,
    result VARCHAR(8),
    remark VARCHAR(128),
    operator VARCHAR(32),
    created_at DATETIME,
    KEY idx_deep_type (process_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 检验记录 ----------------
-- rework_id：判返修后关联的返修批次（闭环可追溯；PASS / SCRAP 判定为空）
CREATE TABLE inspection_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    inspect_no VARCHAR(32) NOT NULL UNIQUE,
    process_type VARCHAR(16) NOT NULL,
    batch_no VARCHAR(32),
    order_no VARCHAR(32),
    defect_type VARCHAR(16),
    defect_size_mm DECIMAL(6,2),
    pos_x DECIMAL(5,2),
    pos_y DECIMAL(5,2),
    verdict VARCHAR(8),
    inspector VARCHAR(32),
    sheet_count INT,
    inspected_at DATETIME,
    rework_id BIGINT,
    KEY idx_inspect_type (defect_type),
    KEY idx_inspect_stage (process_type),
    KEY idx_inspect_rework (rework_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 返修批次（隔离 → 处理中 → 待复检 → 已放行） ----------------
-- 检验 / 深加工 / 成品架共享的批次质量状态。
-- 归并口径：同工序下批次号 / 订单号任一标识相同（或经同一张检验单的批次↔订单桥接）
-- 才归并为同一实物批次；无标识记录各自成批，不误并、不失联。
CREATE TABLE rework_batch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rework_no VARCHAR(32) NOT NULL UNIQUE,
    process_type VARCHAR(16) NOT NULL,
    batch_no VARCHAR(32),
    order_no VARCHAR(32),
    defect_summary VARCHAR(128),
    sheet_count INT DEFAULT 0,
    status VARCHAR(16) NOT NULL,
    operator VARCHAR(32),
    note VARCHAR(255),
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME,
    updated_at DATETIME,
    KEY idx_rework_status (status),
    KEY idx_rework_batch (batch_no),
    KEY idx_rework_order (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 返修时间线事件（全过程审计） ----------------
-- 状态流转、并发冲突、发运拦截都留痕；冲突 / 拦截事件独立事务提交，业务回滚不丢。
CREATE TABLE rework_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rework_id BIGINT NOT NULL,
    event_type VARCHAR(16) NOT NULL,
    from_status VARCHAR(16),
    to_status VARCHAR(16),
    verdict VARCHAR(8),
    operator VARCHAR(32),
    note VARCHAR(255),
    created_at DATETIME,
    KEY idx_event_rework (rework_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 成品架 ----------------
-- area_sqm 是派生列：长 × 宽 × 片数 / 1,000,000，读回时与规格片数反校验。
-- batch_no：架上成品对应的生产批次号，上架 / 发运据此读取共享质量状态（可空）。
CREATE TABLE finished_rack (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rack_no VARCHAR(16) NOT NULL UNIQUE,
    order_no VARCHAR(32) NOT NULL,
    batch_no VARCHAR(32),
    glass_type VARCHAR(32),
    thickness_mm INT,
    length_mm INT,
    width_mm INT,
    sheet_count INT,
    max_load_kg DECIMAL(12,2),
    current_load_kg DECIMAL(12,2),
    area_sqm DECIMAL(12,2),
    ship_status VARCHAR(16),
    shipped_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================================
-- 种子数据
-- 注意：构建期 mysqld 的 NOW() 是 UTC，运行时是东八区，
--       所以时间统一写 UTC_TIMESTAMP() + INTERVAL 8 HOUR。
-- =====================================================================

INSERT INTO storage_location (zone_code, zone_name, rack_code, level_no, batch_count, sheet_count, max_load_kg, current_load_kg, note) VALUES
('A', '原片一区', 'A-01', 3, 1, 200, 6000.00, 4465.20, '靠窗，避免阳光直射'),
('A', '原片一区', 'A-01', 2, 1, 150, 8000.00, 5023.35, '常规货位'),
('A', '原片一区', 'A-01', 1, 1, 120, 12000.00, 8037.36, '底层承重位'),
('A', '原片一区', 'A-02', 3, 1, 60, 6000.00, 3177.90, '有色玻璃专位'),
('A', '原片一区', 'A-02', 2, 1, 55, 8000.00, 3884.10, '常规货位'),
('A', '原片一区', 'A-02', 1, 1, 80, 12000.00, 7144.32, '超白玻专区'),
('A', '原片一区', 'A-03', 3, 1, 45, 5000.00, 5023.35, '已接近承重上限'),
('A', '原片一区', 'A-03', 2, 1, 36, 8000.00, 4018.68, '厚板货位'),
('A', '原片一区', 'A-03', 1, 1, 30, 12000.00, 6355.80, '厚板底层'),
('B', 'Low-E 专区', 'B-01', 2, 1, 100, 8000.00, 5581.50, '恒温恒湿'),
('B', 'Low-E 专区', 'B-01', 1, 1, 90, 12000.00, 6028.02, '恒温恒湿'),
('B', 'Low-E 专区', 'B-02', 2, 1, 70, 8000.00, 4943.40, '膜面朝内放置'),
('B', 'Low-E 专区', 'B-02', 1, 1, 110, 12000.00, 6139.65, '膜面朝内放置'),
('C', '大板区', 'C-01', 2, 1, 18, 10000.00, 4766.85, '需龙门吊配合'),
('C', '大板区', 'C-01', 1, 1, 12, 15000.00, 4025.34, '大板底层');

INSERT INTO glass_stock (batch_no, glass_type, thickness_mm, length_mm, width_mm, sheet_count, supplier, transmittance, inbound_date, location_id, weight_kg, spec_text) VALUES
('YP-2403-01', 'ULTRA_CLEAR', 4,  1830, 1220, 200, '南玻集团', 91.80, '2024-03-11', 1,  4465.20, '4mm 1830×1220'),
('YP-2404-02', 'ULTRA_CLEAR', 6,  1830, 1220, 150, '南玻集团', 91.40, '2024-04-08', 2,  5023.35, '6mm 1830×1220'),
('YP-2401-01', 'FLOAT_CLEAR', 6,  2440, 1830, 120, '台玻集团', 88.50, '2024-01-15', 3,  8037.36, '6mm 2440×1830'),
('YP-2401-03', 'TINTED',      6,  2140, 1650,  60, '信义玻璃', 52.00, '2024-01-22', 4,  3177.90, '6mm 2140×1650'),
('YP-2403-02', 'TINTED',      8,  2140, 1650,  55, '信义玻璃', 45.50, '2024-03-19', 5,  3884.10, '8mm 2140×1650'),
('YP-2401-02', 'ULTRA_CLEAR', 8,  2440, 1830,  80, '南玻集团', 91.60, '2024-01-18', 6,  7144.32, '8mm 2440×1830'),
('YP-2402-02', 'FLOAT_CLEAR', 10, 2440, 1830,  45, '台玻集团', 88.20, '2024-02-06', 7,  5023.35, '10mm 2440×1830'),
('YP-2405-01', 'TINTED',      10, 2440, 1830,  36, '信义玻璃', 38.50, '2024-05-09', 8,  4018.68, '10mm 2440×1830'),
('YP-2402-03', 'FLOAT_CLEAR', 12, 2140, 3300,  30, '金晶科技', 87.60, '2024-02-21', 9,  6355.80, '12mm 2140×3300'),
('YP-2404-01', 'FLOAT_CLEAR', 15, 2140, 3300,  18, '台玻集团', 86.40, '2024-04-17', 14, 4766.85, '15mm 2140×3300'),
('YP-2405-02', 'FLOAT_CLEAR', 19, 2140, 3300,  12, '金晶科技', 85.20, '2024-05-23', 15, 4025.34, '19mm 2140×3300'),
('YP-2402-01', 'LOWE_BASE',   5,  2440, 1830, 100, '旗滨集团', 82.00, '2024-02-03', 10, 5581.50, '5mm 2440×1830'),
('YP-2403-03', 'LOWE_BASE',   6,  2440, 1830,  90, '旗滨集团', 81.50, '2024-03-27', 11, 6028.02, '6mm 2440×1830'),
('YP-2404-03', 'LOWE_BASE',   8,  2140, 1650,  70, '中国玻璃', 80.80, '2024-04-25', 12, 4943.40, '8mm 2140×1650'),
('YP-2405-03', 'ULTRA_CLEAR', 5,  2440, 1830, 110, '南玻集团', 91.20, '2024-05-28', 13, 6139.65, '5mm 2440×1830');

INSERT INTO cutting_order (order_no, stock_id, batch_no, glass_type, layout_no, output_count, source_area_sqm, output_area_sqm, remnant_area_sqm, remnant_spec, cutter, operator, status, created_at, utilization_rate) VALUES
('CUT-20240506-001', 3,  'YP-2401-01', 'FLOAT_CLEAR', 'LAY-0001', 7,  4.465, 4.020, 0.445, '2440×30',  '数控切割线 CTL-01', '张伟', 'DONE',      UTC_TIMESTAMP() + INTERVAL 8 HOUR, 90.03),
('CUT-20240506-002', 4,  'YP-2401-03', 'TINTED',      'LAY-0002', 7,  3.531, 2.900, 0.631, '2140×350', '数控切割线 CTL-02', '李娜', 'DONE',      UTC_TIMESTAMP() + INTERVAL 8 HOUR, 82.13),
('CUT-20240507-003', 6,  'YP-2401-02', 'ULTRA_CLEAR', 'LAY-0003', 5,  4.465, 3.660, 0.805, '940×1000', '数控切割线 CTL-01', '王强', 'DONE',      UTC_TIMESTAMP() + INTERVAL 8 HOUR, 81.97),
('CUT-20240507-004', 12, 'YP-2402-01', 'LOWE_BASE',   'LAY-0004', 4,  4.465, 3.740, 0.725, '240×1830', '数控切割线 CTL-03', '张伟', 'DONE',      UTC_TIMESTAMP() + INTERVAL 8 HOUR, 83.76),
('CUT-20240508-005', 9,  'YP-2402-03', 'FLOAT_CLEAR', 'LAY-0005', 6,  7.062, 6.600, 0.462, '2140×300', '龙门切割线 CTL-04', '王强', 'DONE',      UTC_TIMESTAMP() + INTERVAL 8 HOUR, 93.46),
('CUT-20240509-006', 13, 'YP-2403-03', 'LOWE_BASE',   'LAY-0006', 10, 4.465, 3.960, 0.505, '400×1830', '数控切割线 CTL-01', '李娜', 'CUTTING',   UTC_TIMESTAMP() + INTERVAL 8 HOUR, 88.68),
('CUT-20240510-007', 7,  'YP-2402-02', 'FLOAT_CLEAR', 'LAY-0007', 4,  4.465, 2.000, 2.465, '2440×1130','数控切割线 CTL-02', '赵敏', 'OPTIMIZED', UTC_TIMESTAMP() + INTERVAL 8 HOUR, 44.79),
('CUT-20240511-008', 8,  'YP-2405-01', 'TINTED',      'LAY-0008', 7,  4.465, 3.940, 0.525, '440×1830', '数控切割线 CTL-03', '赵敏', 'OPTIMIZED', UTC_TIMESTAMP() + INTERVAL 8 HOUR, 88.24);

INSERT INTO cutting_piece (cutting_order_id, sort_no, length_mm, width_mm, quantity, area_sqm, remark) VALUES
(1, 1, 1200, 900, 2, 2.1600, '幕墙大板'),
(1, 2,  800, 600, 3, 1.4400, '窗扇'),
(1, 3,  700, 300, 2, 0.4200, '压条'),
(2, 1, 1000, 800, 2, 1.6000, '隔断'),
(2, 2,  700, 500, 2, 0.7000, '门芯板'),
(2, 3,  500, 400, 3, 0.6000, '小料'),
(3, 1, 1500, 1000, 1, 1.5000, '超大板'),
(3, 2,  900, 800, 2, 1.4400, '展示柜'),
(3, 3,  600, 600, 2, 0.7200, '方片'),
(4, 1, 1100, 900, 2, 1.9800, 'Low-E 中空基片'),
(4, 2, 1100, 800, 2, 1.7600, 'Low-E 中空基片'),
(5, 1, 1600, 1200, 2, 3.8400, '厚板大料'),
(5, 2, 1000, 900, 2, 1.8000, '厚板中料'),
(5, 3,  800, 600, 2, 0.9600, '厚板小料'),
(6, 1,  800, 600, 6, 2.8800, '标准窗扇'),
(6, 2,  600, 450, 4, 1.0800, '腰窗'),
(7, 1, 1000, 700, 2, 1.4000, '待优化'),
(7, 2,  600, 500, 2, 0.6000, '待优化'),
(8, 1, 1200, 600, 3, 2.1600, '长条板'),
(8, 2,  900, 600, 2, 1.0800, '门板'),
(8, 3,  700, 500, 2, 0.7000, '补片');

INSERT INTO edge_grinding (record_no, cutting_order_id, batch_no, machine, edge_type, wheel_model, feed_speed, piece_count, chip_count, chip_note, operator, grind_time) VALUES
('EG-20240506-001', 1, 'YP-2401-01', '双边磨边机 MB-01', 'FLAT',  'DZ-120#', 4.50, 7,  0, '无崩边',                     '张伟', UTC_TIMESTAMP() + INTERVAL 8 HOUR),
('EG-20240506-002', 2, 'YP-2401-03', '双边磨边机 MB-02', 'ROUND', 'YB-80#',  3.20, 7,  1, '1 片边部微崩，已复磨',       '李娜', UTC_TIMESTAMP() + INTERVAL 8 HOUR),
('EG-20240507-003', 3, 'YP-2401-02', '双边磨边机 MB-01', 'BEVEL', 'XB-60#',  2.40, 5,  0, '斜边光洁度良好',             '王强', UTC_TIMESTAMP() + INTERVAL 8 HOUR),
('EG-20240507-004', 4, 'YP-2402-01', '双边磨边机 MB-03', 'FLAT',  'DZ-120#', 5.00, 4,  0, '直边一次合格',               '张伟', UTC_TIMESTAMP() + INTERVAL 8 HOUR),
('EG-20240508-005', 5, 'YP-2402-03', '双边磨边机 MB-02', 'DUCK',  'YZ-45#',  1.80, 6,  2, '2 片鸭嘴边崩边超差，转报废', '赵敏', UTC_TIMESTAMP() + INTERVAL 8 HOUR),
('EG-20240509-006', 6, 'YP-2403-03', '双边磨边机 MB-01', 'ROUND', 'YB-80#',  2.80, 10, 1, '1 片圆边过渡不顺，已返修',   '李娜', UTC_TIMESTAMP() + INTERVAL 8 HOUR),
('EG-20240510-007', 7, 'YP-2402-02', '双边磨边机 MB-03', 'BEVEL', 'XB-60#',  1.90, 4,  0, '斜边合格',                   '王强', UTC_TIMESTAMP() + INTERVAL 8 HOUR),
('EG-20240511-008', 8, 'YP-2405-01', '双边磨边机 MB-02', 'FLAT',  'DZ-120#', 4.20, 7,  1, '1 片直边轻微崩边',           '赵敏', UTC_TIMESTAMP() + INTERVAL 8 HOUR);

INSERT INTO tempering_batch (batch_no, furnace_no, glass_type, thickness_mm, temp_low, temp_high, temp_range, heat_temp, heat_seconds, quench_pressure, warpage, particle_count, output_count, broken_count, stage, operator, created_at) VALUES
('TB-20240506-001', 'TG-01', 'FLOAT_CLEAR',  6,  660, 700, '660-700', 685, 240, 3.20, 0.120, 45, 7,  0, 'DONE',    '陈刚', UTC_TIMESTAMP() + INTERVAL 8 HOUR),
('TB-20240506-002', 'TG-01', 'TINTED',       6,  650, 690, '650-690', 668, 210, 2.90, 0.150, 42, 7,  1, 'DONE',    '陈刚', UTC_TIMESTAMP() + INTERVAL 8 HOUR),
('TB-20240507-003', 'TG-02', 'ULTRA_CLEAR',  8,  680, 720, '680-720', 700, 300, 3.60, 0.100, 52, 5,  0, 'DONE',    '刘洋', UTC_TIMESTAMP() + INTERVAL 8 HOUR),
('TB-20240507-004', 'TG-02', 'LOWE_BASE',    5,  640, 680, '640-680', 662, 180, 2.60, 0.180, 38, 4,  1, 'DONE',    '刘洋', UTC_TIMESTAMP() + INTERVAL 8 HOUR),
('TB-20240508-005', 'TG-01', 'FLOAT_CLEAR',  12, 690, 720, '690-720', 712, 420, 4.20, 0.090, 58, 6,  0, 'DONE',    '陈刚', UTC_TIMESTAMP() + INTERVAL 8 HOUR),
('TB-20240509-006', 'TG-03', 'LOWE_BASE',    6,  655, 695, '655-695', 675, 240, 3.10, 0.130, 46, 10, 2, 'DONE',    '孙磊', UTC_TIMESTAMP() + INTERVAL 8 HOUR),
('TB-20240510-007', 'TG-03', 'TINTED',       10, 670, 710, '670-710', 690, 360, 3.80, 0.110, 50, 7,  1, 'QUENCH',  '孙磊', UTC_TIMESTAMP() + INTERVAL 8 HOUR),
('TB-20240511-008', 'TG-02', 'FLOAT_CLEAR',  10, 675, 715, '675-715', 695, 330, 3.40, 0.140, 48, 0,  0, 'HEATING', '刘洋', UTC_TIMESTAMP() + INTERVAL 8 HOUR);

INSERT INTO deep_process_record (record_no, process_type, batch_no, glass_type, thickness_mm, piece_count, film_system, target_material, film_thickness_nm, emissivity, spacer, gas_type, dew_point, pvb_thickness, autoclave_temp, autoclave_pressure, autoclave_minutes, result, remark, operator, created_at) VALUES
('DP-20240506-001', 'COATING',    'YP-2402-01', 'LOWE_BASE',   5,  60, '单银 Low-E',   '银 Ag',         120, 0.150, NULL,        NULL,   NULL, NULL, NULL, NULL, NULL,  'PASS', '辐射率 0.150 达到节能要求',                    '周涛', UTC_TIMESTAMP() + INTERVAL 8 HOUR),
('DP-20240507-002', 'COATING',    'YP-2403-03', 'LOWE_BASE',   6,  80, '双银 Low-E',   '银 Ag',         180, 0.080, NULL,        NULL,   NULL, NULL, NULL, NULL, NULL,  'PASS', '双银膜系，遮蔽系数 0.38',                      '周涛', UTC_TIMESTAMP() + INTERVAL 8 HOUR),
('DP-20240508-003', 'COATING',    'YP-2401-02', 'ULTRA_CLEAR', 8,  40, '阳光控制膜',   '氧化锡 SnO2',   240, 0.420, NULL,        NULL,   NULL, NULL, NULL, NULL, NULL,  'PASS', '热反射膜，遮阳效果良好',                      '周涛', UTC_TIMESTAMP() + INTERVAL 8 HOUR),
('DP-20240506-004', 'INSULATING', 'YP-2401-01', 'FLOAT_CLEAR', 6,  50, NULL,           NULL,            NULL, NULL,  '铝条 12A',   'ARGON', -52,  NULL, NULL, NULL, NULL, 'PASS', '露点 -52℃ 合格',                              '许静', UTC_TIMESTAMP() + INTERVAL 8 HOUR),
('DP-20240507-005', 'INSULATING', 'YP-2401-03', 'TINTED',      6,  30, NULL,           NULL,            NULL, NULL,  '铝条 9A',    'AIR',   -35,  NULL, NULL, NULL, NULL, 'FAIL', '露点 -35℃ 高于 -40℃，判不合格，需重新干燥',  '许静', UTC_TIMESTAMP() + INTERVAL 8 HOUR),
('DP-20240508-006', 'INSULATING', 'YP-2402-02', 'FLOAT_CLEAR', 10, 24, NULL,           NULL,            NULL, NULL,  '暖边条 12A', 'ARGON', -48,  NULL, NULL, NULL, NULL, 'PASS', '暖边间隔条，露点 -48℃ 合格',                  '许静', UTC_TIMESTAMP() + INTERVAL 8 HOUR),
('DP-20240510-007', 'INSULATING', 'YP-2404-03', 'LOWE_BASE',   8,  36, NULL,           NULL,            NULL, NULL,  '铝条 6A',    'AIR',   -28,  NULL, NULL, NULL, NULL, 'FAIL', '露点 -28℃ 不合格，分子筛已受潮',              '许静', UTC_TIMESTAMP() + INTERVAL 8 HOUR),
('DP-20240509-008', 'LAMINATING', 'YP-2401-01', 'FLOAT_CLEAR', 6,  20, NULL,           NULL,            NULL, NULL,  NULL,         NULL,    NULL, 0.38, 135,  1.20, 90,   'PASS', 'PVB 0.38mm 与 6mm 玻璃匹配，高压釜 135℃/90min', '何磊', UTC_TIMESTAMP() + INTERVAL 8 HOUR),
('DP-20240510-009', 'LAMINATING', 'YP-2402-02', 'FLOAT_CLEAR', 10, 16, NULL,           NULL,            NULL, NULL,  NULL,         NULL,    NULL, 0.76, 140,  1.30, 120,  'PASS', 'PVB 0.76mm 与 10mm 玻璃匹配',                  '何磊', UTC_TIMESTAMP() + INTERVAL 8 HOUR),
('DP-20240511-010', 'LAMINATING', 'YP-2404-01', 'FLOAT_CLEAR', 15, 10, NULL,           NULL,            NULL, NULL,  NULL,         NULL,    NULL, 1.52, 145,  1.35, 150,  'PASS', 'PVB 1.52mm 与 15mm 玻璃匹配',                  '何磊', UTC_TIMESTAMP() + INTERVAL 8 HOUR);

INSERT INTO inspection_record (inspect_no, process_type, batch_no, order_no, defect_type, defect_size_mm, pos_x, pos_y, verdict, inspector, sheet_count, inspected_at) VALUES
('IR-20240506-001', 'CUTTING',    'YP-2401-01', 'CUT-20240506-001', 'SCRATCH', 12.00, 18.50, 22.00, 'REWORK', '周梅', 2,  UTC_TIMESTAMP() + INTERVAL 8 HOUR),
('IR-20240506-002', 'CUTTING',    'YP-2401-03', 'CUT-20240506-002', 'CHIP',     3.50, 62.00,  8.50, 'SCRAP',  '周梅', 1,  UTC_TIMESTAMP() + INTERVAL 8 HOUR),
('IR-20240506-003', 'GRINDING',   'YP-2401-03', 'EG-20240506-002',  'CHIP',     2.20, 74.00, 15.00, 'REWORK', '吴静', 1,  UTC_TIMESTAMP() + INTERVAL 8 HOUR),
('IR-20240508-004', 'GRINDING',   'YP-2402-03', 'EG-20240508-005',  'CHIP',     5.80, 40.00, 88.00, 'SCRAP',  '吴静', 2,  UTC_TIMESTAMP() + INTERVAL 8 HOUR),
('IR-20240507-005', 'TEMPERING',  'YP-2401-02', 'TB-20240507-003',  'WAVE',     0.35, 50.00, 45.00, 'PASS',   '何斌', 5,  UTC_TIMESTAMP() + INTERVAL 8 HOUR),
('IR-20240507-006', 'TEMPERING',  'YP-2402-01', 'TB-20240507-004',  'STRESS',   8.00, 30.00, 60.00, 'REWORK', '何斌', 1,  UTC_TIMESTAMP() + INTERVAL 8 HOUR),
('IR-20240508-007', 'TEMPERING',  'YP-2402-03', 'TB-20240508-005',  'WAVE',     0.28, 66.00, 33.00, 'PASS',   '何斌', 6,  UTC_TIMESTAMP() + INTERVAL 8 HOUR),
('IR-20240509-008', 'TEMPERING',  'YP-2403-03', 'TB-20240509-006',  'STRESS',   6.50, 22.00, 70.00, 'PASS',   '何斌', 8,  UTC_TIMESTAMP() + INTERVAL 8 HOUR),
('IR-20240506-009', 'COATING',    'YP-2402-01', 'DP-20240506-001',  'SCRATCH',  4.20, 55.00, 25.00, 'REWORK', '周梅', 2,  UTC_TIMESTAMP() + INTERVAL 8 HOUR),
('IR-20240507-010', 'COATING',    'YP-2403-03', 'DP-20240507-002',  'BUBBLE',   1.20, 80.00, 55.00, 'PASS',   '周梅', 1,  UTC_TIMESTAMP() + INTERVAL 8 HOUR),
('IR-20240508-011', 'COATING',    'YP-2401-02', 'DP-20240508-003',  'STONE',    0.80, 12.00, 78.00, 'SCRAP',  '周梅', 1,  UTC_TIMESTAMP() + INTERVAL 8 HOUR),
('IR-20240510-012', 'INSULATING', 'YP-2404-03', 'DP-20240510-007',  'BUBBLE',   2.60, 45.00, 40.00, 'SCRAP',  '吴静', 3,  UTC_TIMESTAMP() + INTERVAL 8 HOUR),
('IR-20240506-013', 'INSULATING', 'YP-2401-01', 'DP-20240506-004',  'SCRATCH',  6.40, 70.00, 18.00, 'REWORK', '吴静', 1,  UTC_TIMESTAMP() + INTERVAL 8 HOUR),
('IR-20240509-014', 'LAMINATING', 'YP-2401-01', 'DP-20240509-008',  'BUBBLE',   1.50, 35.00, 62.00, 'PASS',   '何斌', 20, UTC_TIMESTAMP() + INTERVAL 8 HOUR),
('IR-20240511-015', 'LAMINATING', 'YP-2404-01', 'DP-20240511-010',  'STONE',    0.90, 88.00, 30.00, 'PASS',   '何斌', 10, UTC_TIMESTAMP() + INTERVAL 8 HOUR),
-- 历史遗留判返修记录：有的只有批次号、有的只有订单号，启动时按「同工序 + 标识桥接」归并进返修批次
('IR-20240415-101', 'TEMPERING',  'YP-2402-01', NULL,               'WAVE',     0.42, 33.00, 51.00, 'REWORK', '何斌', 2,  UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 20 DAY),
('IR-20240416-102', 'COATING',    NULL,         'DP-20240506-001',  'BUBBLE',   0.90, 61.00, 44.00, 'REWORK', '周梅', 1,  UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 19 DAY),
('IR-20240418-103', 'GRINDING',   NULL,         'EG-20240418-090',  'CHIP',     4.10, 25.00, 70.00, 'REWORK', '吴静', 1,  UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 17 DAY);

-- 已发运（R-03 / R-07）不回填批次号：既有发运数据保持原样，不做倒改；
-- 在库架按所装成品批次回填，用于演示共享质量状态与发运拦截。
INSERT INTO finished_rack (rack_no, order_no, batch_no, glass_type, thickness_mm, length_mm, width_mm, sheet_count, max_load_kg, current_load_kg, area_sqm, ship_status, shipped_at) VALUES
('R-01', 'DD-2405-018', 'YP-2401-01', 'FLOAT_CLEAR',  6,  1200, 900,  24, 3000.00, 388.80,  25.92, 'READY',   NULL),
('R-02', 'DD-2405-019', 'YP-2401-02', 'ULTRA_CLEAR',  8,  1500, 1000, 16, 3000.00, 480.00,  24.00, 'READY',   NULL),
('R-03', 'DD-2405-021', NULL,         'TINTED',       6,  1000, 800,  30, 3000.00, 360.00,  24.00, 'SHIPPED', UTC_TIMESTAMP() + INTERVAL 8 HOUR),
('R-04', 'DD-2405-022', 'YP-2402-01', 'LOWE_BASE',    5,  1100, 900,  20, 2500.00, 247.50,  19.80, 'READY',   NULL),
('R-05', 'DD-2405-024', 'YP-2402-03', 'FLOAT_CLEAR',  12, 1600, 1200, 12, 4000.00,   0.00,  23.04, 'PENDING', NULL),
('R-06', 'DD-2405-025', 'YP-2403-03', 'LOWE_BASE',    6,  800,  600,  40, 2500.00, 288.00,  19.20, 'READY',   NULL),
('R-07', 'DD-2405-027', NULL,         'FLOAT_CLEAR',  10, 1200, 600,  18, 3000.00, 324.00,  12.96, 'SHIPPED', UTC_TIMESTAMP() + INTERVAL 8 HOUR),
('R-08', 'DD-2405-028', NULL,         'ULTRA_CLEAR',  4,  1830, 1220, 36, 3000.00, 803.70,  80.37, 'READY',   NULL),
('R-09', 'DD-2405-030', NULL,         'TINTED',       10, 900,  600,  22, 3000.00,   0.00,  11.88, 'PENDING', NULL),
('R-10', 'DD-2405-031', NULL,         'FLOAT_CLEAR',  15, 700,  500,  10, 3000.00, 131.25,   3.50, 'READY',   NULL);
