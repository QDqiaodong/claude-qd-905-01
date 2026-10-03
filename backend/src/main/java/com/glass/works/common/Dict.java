package com.glass.works.common;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 玻璃深加工厂词典与工艺参数表：
 * 玻璃种类、厚度系列、膜系、深加工工序、磨边边型、缺陷类型、检验判定、切割单状态、
 * 以及钢化温度区间 / 中空露点 / PVB 与玻璃厚度匹配 / 成品架承重等业务规则常量。
 * 规则集中在此，便于出题时被要求调整或扩展。
 */
public final class Dict {

    private Dict() {
    }

    // ---------------- 玻璃种类 ----------------
    public static final String GLASS_FLOAT = "FLOAT_CLEAR";
    public static final String GLASS_ULTRA = "ULTRA_CLEAR";
    public static final String GLASS_TINTED = "TINTED";
    public static final String GLASS_LOWE = "LOWE_BASE";

    public static final List<String> GLASS_TYPES =
            List.of(GLASS_FLOAT, GLASS_ULTRA, GLASS_TINTED, GLASS_LOWE);

    private static final Map<String, String> GLASS_TYPE_NAME = Map.of(
            GLASS_FLOAT, "浮法白玻",
            GLASS_ULTRA, "超白玻",
            GLASS_TINTED, "有色玻璃",
            GLASS_LOWE, "Low-E 基片");

    /** 前端画玻璃块时按种类取色（半透明玻璃质感用） */
    private static final Map<String, String> GLASS_TYPE_COLOR = Map.of(
            GLASS_FLOAT, "#4fa8d8",
            GLASS_ULTRA, "#4fd1c5",
            GLASS_TINTED, "#7c6fd0",
            GLASS_LOWE, "#d4a72c");

    /** 各类玻璃基准透光率 %（创建原片时未填则取该值） */
    private static final Map<String, BigDecimal> GLASS_TYPE_TRANSMITTANCE = Map.of(
            GLASS_FLOAT, BigDecimal.valueOf(88.5),
            GLASS_ULTRA, BigDecimal.valueOf(91.6),
            GLASS_TINTED, BigDecimal.valueOf(52.0),
            GLASS_LOWE, BigDecimal.valueOf(82.0));

    // ---------------- 厚度系列 ----------------
    public static final List<Integer> THICKNESS_OPTIONS = List.of(4, 5, 6, 8, 10, 12, 15, 19);

    /** 玻璃密度 2.5 t/m³ → 单㎡每 mm 厚度约 2.5 kg */
    public static final BigDecimal GLASS_KG_PER_SQM_MM = BigDecimal.valueOf(2.5);

    // ---------------- 磨边边型 ----------------
    public static final String EDGE_FLAT = "FLAT";
    public static final String EDGE_ROUND = "ROUND";
    public static final String EDGE_BEVEL = "BEVEL";
    public static final String EDGE_DUCK = "DUCK";

    public static final List<String> EDGE_TYPES = List.of(EDGE_FLAT, EDGE_ROUND, EDGE_BEVEL, EDGE_DUCK);

    private static final Map<String, String> EDGE_TYPE_NAME = Map.of(
            EDGE_FLAT, "直边",
            EDGE_ROUND, "圆边",
            EDGE_BEVEL, "斜边",
            EDGE_DUCK, "鸭嘴边");

    /** 各边型允许的进给速度区间 m/min */
    private static final Map<String, double[]> EDGE_FEED_RANGE = Map.of(
            EDGE_FLAT, new double[]{1.0, 8.0},
            EDGE_ROUND, new double[]{0.8, 6.0},
            EDGE_BEVEL, new double[]{0.6, 4.5},
            EDGE_DUCK, new double[]{0.5, 3.5});

    // ---------------- 钢化炉 ----------------
    /** 加热温度允许区间 ℃ */
    public static final double TEMP_MIN = 620.0;
    public static final double TEMP_MAX = 720.0;
    /** 加热时长允许区间 s */
    public static final int HEAT_SECONDS_MIN = 90;
    public static final int HEAT_SECONDS_MAX = 600;
    /** 淬冷风压允许区间 kPa */
    public static final double QUENCH_MIN = 1.5;
    public static final double QUENCH_MAX = 6.5;

    /** 炉体分段：加热段 → 淬冷段 → 冷却段 */
    public static final List<String> FURNACE_STAGES = List.of("HEATING", "QUENCH", "COOLING");

    private static final Map<String, String> STAGE_NAME = Map.of(
            "HEATING", "加热段",
            "QUENCH", "淬冷段",
            "COOLING", "冷却段",
            "DONE", "已出炉");

    // ---------------- 深加工工序 ----------------
    public static final String PROCESS_COATING = "COATING";
    public static final String PROCESS_INSULATING = "INSULATING";
    public static final String PROCESS_LAMINATING = "LAMINATING";

    public static final List<String> PROCESS_TYPES =
            List.of(PROCESS_COATING, PROCESS_INSULATING, PROCESS_LAMINATING);

    private static final Map<String, String> PROCESS_TYPE_NAME = Map.of(
            PROCESS_COATING, "镀膜",
            PROCESS_INSULATING, "中空",
            PROCESS_LAMINATING, "夹胶");

    /** 全工序环节（含前道），检验记录按此分类统计合格 / 返修 / 报废 */
    public static final String STAGE_CUTTING = "CUTTING";
    public static final String STAGE_GRINDING = "GRINDING";
    public static final String STAGE_TEMPERING = "TEMPERING";

    public static final List<String> WORK_STAGES = List.of(
            STAGE_CUTTING, STAGE_GRINDING, STAGE_TEMPERING,
            PROCESS_COATING, PROCESS_INSULATING, PROCESS_LAMINATING);

    private static final Map<String, String> WORK_STAGE_NAME = Map.of(
            STAGE_CUTTING, "切割",
            STAGE_GRINDING, "磨边",
            STAGE_TEMPERING, "钢化",
            PROCESS_COATING, "镀膜",
            PROCESS_INSULATING, "中空",
            PROCESS_LAMINATING, "夹胶");

    /** 镀膜膜系 */
    public static final List<String> FILM_SYSTEMS =
            List.of("单银 Low-E", "双银 Low-E", "三银 Low-E", "阳光控制膜", "热反射膜");

    /** 镀膜靶材 */
    public static final List<String> TARGET_MATERIALS =
            List.of("银 Ag", "氧化锡 SnO2", "氧化锌 ZnO", "氮化硅 Si3N4", "钛 Ti", "镍铬 NiCr");

    /** 中空间隔条 */
    public static final List<String> SPACERS = List.of("铝条 6A", "铝条 9A", "铝条 12A", "暖边条 12A", "不锈钢 16A");

    /** 中空气体 */
    public static final List<String> GAS_TYPES = List.of("AIR", "ARGON", "KRYPTON");

    private static final Map<String, String> GAS_TYPE_NAME = Map.of(
            "AIR", "空气",
            "ARGON", "氩气",
            "KRYPTON", "氪气");

    /** 中空露点合格上限 ℃：高于该值判不合格 */
    public static final double DEW_POINT_LIMIT = -40.0;

    /** 夹胶 PVB 厚度系列 mm */
    public static final List<Double> PVB_OPTIONS = List.of(0.38, 0.76, 1.14, 1.52);

    /** 玻璃厚度 → 允许使用的 PVB 厚度（不匹配不允许投产） */
    private static final Map<Integer, Set<Double>> PVB_MATCH = Map.of(
            4, Set.of(0.38),
            5, Set.of(0.38),
            6, Set.of(0.38, 0.76),
            8, Set.of(0.76),
            10, Set.of(0.76, 1.14),
            12, Set.of(1.14, 1.52),
            15, Set.of(1.52),
            19, Set.of(1.52));

    /** 高压釜工艺区间 */
    public static final double AUTOCLAVE_TEMP_MIN = 120.0;
    public static final double AUTOCLAVE_TEMP_MAX = 150.0;
    public static final double AUTOCLAVE_PRESSURE_MIN = 1.0;
    public static final double AUTOCLAVE_PRESSURE_MAX = 1.4;

    // ---------------- 缺陷类型 ----------------
    public static final String DEFECT_SCRATCH = "SCRATCH";
    public static final String DEFECT_BUBBLE = "BUBBLE";
    public static final String DEFECT_STONE = "STONE";
    public static final String DEFECT_CHIP = "CHIP";
    public static final String DEFECT_WAVE = "WAVE";
    public static final String DEFECT_STRESS = "STRESS";

    public static final List<String> DEFECT_TYPES = List.of(
            DEFECT_SCRATCH, DEFECT_BUBBLE, DEFECT_STONE, DEFECT_CHIP, DEFECT_WAVE, DEFECT_STRESS);

    private static final Map<String, String> DEFECT_TYPE_NAME = Map.of(
            DEFECT_SCRATCH, "划伤",
            DEFECT_BUBBLE, "气泡",
            DEFECT_STONE, "结石",
            DEFECT_CHIP, "崩边",
            DEFECT_WAVE, "波形",
            DEFECT_STRESS, "应力斑");

    /** 缺陷标记点配色 */
    private static final Map<String, String> DEFECT_TYPE_COLOR = Map.of(
            DEFECT_SCRATCH, "#e4573d",
            DEFECT_BUBBLE, "#3fa7e0",
            DEFECT_STONE, "#8c6bd6",
            DEFECT_CHIP, "#e8a33d",
            DEFECT_WAVE, "#39b58a",
            DEFECT_STRESS, "#d94f8a");

    // ---------------- 检验判定 ----------------
    public static final String VERDICT_PASS = "PASS";
    public static final String VERDICT_REWORK = "REWORK";
    public static final String VERDICT_SCRAP = "SCRAP";

    public static final List<String> VERDICTS = List.of(VERDICT_PASS, VERDICT_REWORK, VERDICT_SCRAP);

    private static final Map<String, String> VERDICT_NAME = Map.of(
            VERDICT_PASS, "合格",
            VERDICT_REWORK, "返修",
            VERDICT_SCRAP, "报废");

    // ---------------- 切割单状态 ----------------
    public static final String CUT_DRAFT = "DRAFT";
    public static final String CUT_OPTIMIZED = "OPTIMIZED";
    public static final String CUT_CUTTING = "CUTTING";
    public static final String CUT_DONE = "DONE";

    private static final Map<String, String> CUT_STATUS_NAME = Map.of(
            CUT_DRAFT, "待排产",
            CUT_OPTIMIZED, "已优化",
            CUT_CUTTING, "切割中",
            CUT_DONE, "已完成");

    /** 利用率警戒线：低于该值为橙色预警 */
    public static final double UTIL_WARN = 70.0;
    /** 利用率优秀线：高于该值为绿色 */
    public static final double UTIL_GOOD = 95.0;

    // ---------------- 成品架发货状态 ----------------
    public static final String SHIP_PENDING = "PENDING";
    public static final String SHIP_READY = "READY";
    public static final String SHIP_SHIPPED = "SHIPPED";

    private static final Map<String, String> SHIP_STATUS_NAME = Map.of(
            SHIP_PENDING, "待备货",
            SHIP_READY, "待发货",
            SHIP_SHIPPED, "已发货");

    // ---------------- 返修批次状态（受控流转） ----------------
    /** 隔离：判返修后初始状态，等待登记处置 */
    public static final String RW_ISOLATED = "ISOLATED";
    /** 处理中：已登记处置，返修进行中 */
    public static final String RW_PROCESSING = "PROCESSING";
    /** 待复检：处置完成，等待复检判定 */
    public static final String RW_PENDING_RECHECK = "PENDING_RECHECK";
    /** 已放行：复检合格，闭环终态 */
    public static final String RW_RELEASED = "RELEASED";

    public static final List<String> REWORK_STATUSES =
            List.of(RW_ISOLATED, RW_PROCESSING, RW_PENDING_RECHECK, RW_RELEASED);

    private static final Map<String, String> REWORK_STATUS_NAME = Map.of(
            RW_ISOLATED, "隔离",
            RW_PROCESSING, "处理中",
            RW_PENDING_RECHECK, "待复检",
            RW_RELEASED, "已放行");

    // ---------------- 返修时间线事件类型 ----------------
    public static final String EV_CREATE = "CREATE";        // 新判返修，建批隔离
    public static final String EV_MERGE = "MERGE";          // 历史记录归并建批
    public static final String EV_APPEND = "APPEND";        // 新返修判定并入在制批次
    public static final String EV_DISPOSE = "DISPOSE";      // 登记处置，进入处理中
    public static final String EV_SUBMIT = "SUBMIT";        // 处置完成，提交复检
    public static final String EV_RECHECK = "RECHECK";      // 复检判定（合格放行 / 再判返修回隔离）
    public static final String EV_CONFLICT = "CONFLICT";    // 并发结论冲突，未生效
    public static final String EV_SHIP_BLOCK = "SHIP_BLOCK"; // 上架 / 发运被质量状态拦截

    private static final Map<String, String> REWORK_EVENT_NAME = Map.of(
            EV_CREATE, "判返修隔离",
            EV_MERGE, "历史归并建批",
            EV_APPEND, "并入在制批次",
            EV_DISPOSE, "登记处置",
            EV_SUBMIT, "提交复检",
            EV_RECHECK, "复检判定",
            EV_CONFLICT, "并发冲突",
            EV_SHIP_BLOCK, "发运拦截");

    // ---------------- 取值方法 ----------------

    public static boolean isValidGlassType(String type) {
        return type != null && GLASS_TYPE_NAME.containsKey(type);
    }

    public static String glassTypeName(String type) {
        return type == null ? "" : GLASS_TYPE_NAME.getOrDefault(type, type);
    }

    public static String glassTypeColor(String type) {
        return type == null ? "#4fa8d8" : GLASS_TYPE_COLOR.getOrDefault(type, "#4fa8d8");
    }

    public static BigDecimal glassTypeTransmittance(String type) {
        return GLASS_TYPE_TRANSMITTANCE.getOrDefault(type, BigDecimal.valueOf(88.5));
    }

    public static boolean isValidThickness(Integer mm) {
        return mm != null && THICKNESS_OPTIONS.contains(mm);
    }

    public static boolean isValidEdgeType(String edge) {
        return edge != null && EDGE_TYPE_NAME.containsKey(edge);
    }

    public static String edgeTypeName(String edge) {
        return edge == null ? "" : EDGE_TYPE_NAME.getOrDefault(edge, edge);
    }

    public static double[] edgeFeedRange(String edge) {
        return EDGE_FEED_RANGE.getOrDefault(edge, new double[]{0.5, 8.0});
    }

    public static boolean isValidProcessType(String type) {
        return type != null && PROCESS_TYPE_NAME.containsKey(type);
    }

    public static String processTypeName(String type) {
        return type == null ? "" : PROCESS_TYPE_NAME.getOrDefault(type, type);
    }

    public static boolean isValidWorkStage(String stage) {
        return stage != null && WORK_STAGE_NAME.containsKey(stage);
    }

    public static String workStageName(String stage) {
        return stage == null ? "" : WORK_STAGE_NAME.getOrDefault(stage, stage);
    }

    public static String gasTypeName(String gas) {
        return gas == null ? "" : GAS_TYPE_NAME.getOrDefault(gas, gas);
    }

    public static boolean isValidGas(String gas) {
        return gas != null && GAS_TYPE_NAME.containsKey(gas);
    }

    /** 夹胶投产前校验：PVB 厚度必须与该玻璃厚度匹配 */
    public static boolean isPvbMatched(Integer glassThicknessMm, Double pvbThickness) {
        if (glassThicknessMm == null || pvbThickness == null) {
            return false;
        }
        Set<Double> allowed = PVB_MATCH.get(glassThicknessMm);
        return allowed != null && allowed.contains(pvbThickness);
    }

    public static Set<Double> pvbAllowed(Integer glassThicknessMm) {
        return PVB_MATCH.getOrDefault(glassThicknessMm, Set.of());
    }

    public static boolean isValidDefectType(String type) {
        return type != null && DEFECT_TYPE_NAME.containsKey(type);
    }

    public static String defectTypeName(String type) {
        return type == null ? "" : DEFECT_TYPE_NAME.getOrDefault(type, type);
    }

    public static String defectTypeColor(String type) {
        return type == null ? "#e4573d" : DEFECT_TYPE_COLOR.getOrDefault(type, "#e4573d");
    }

    public static boolean isValidVerdict(String verdict) {
        return verdict != null && VERDICT_NAME.containsKey(verdict);
    }

    public static String verdictName(String verdict) {
        return verdict == null ? "" : VERDICT_NAME.getOrDefault(verdict, verdict);
    }

    public static boolean isValidCutStatus(String status) {
        return status != null && CUT_STATUS_NAME.containsKey(status);
    }

    public static String cutStatusName(String status) {
        return status == null ? "" : CUT_STATUS_NAME.getOrDefault(status, status);
    }

    public static boolean isValidShipStatus(String status) {
        return status != null && SHIP_STATUS_NAME.containsKey(status);
    }

    public static String shipStatusName(String status) {
        return status == null ? "" : SHIP_STATUS_NAME.getOrDefault(status, status);
    }

    public static boolean isValidReworkStatus(String status) {
        return status != null && REWORK_STATUS_NAME.containsKey(status);
    }

    public static String reworkStatusName(String status) {
        return status == null ? "" : REWORK_STATUS_NAME.getOrDefault(status, status);
    }

    public static String reworkEventName(String eventType) {
        return eventType == null ? "" : REWORK_EVENT_NAME.getOrDefault(eventType, eventType);
    }

    public static String stageName(String stage) {
        return stage == null ? "" : STAGE_NAME.getOrDefault(stage, stage);
    }

    /** 边型 + 参考进给速度，供页面下拉展示（有序） */
    public static Map<String, String> edgeTypeOptions() {
        Map<String, String> options = new LinkedHashMap<>();
        for (String edge : EDGE_TYPES) {
            double[] range = edgeFeedRange(edge);
            options.put(edge, edgeTypeName(edge) + "（进给 " + range[0] + "~" + range[1] + " m/min）");
        }
        return options;
    }
}
