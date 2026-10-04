package com.glass.works.dto;

import com.glass.works.domain.InspectionRecord;
import com.glass.works.domain.ReworkBatch;
import com.glass.works.domain.ReworkEvent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 页面视图对象：检验缺陷分布图、钢化炉当炉统计。 */
public final class Views {

    private Views() {
    }

    /** 缺陷分布图上的一个缺陷点 */
    @Data
    @NoArgsConstructor
    public static class DefectPointVO {
        private Long id;
        private String inspectNo;
        private String defectType;
        private String defectTypeName;
        private String color;
        private BigDecimal defectSizeMm;
        private BigDecimal posX;
        private BigDecimal posY;
        private String verdict;
        private String verdictName;
        private String batchNo;
        private String processType;
        private String processTypeName;
        private String inspector;
        private Integer sheetCount;
    }

    /** 检验缺陷分布图整体视图 */
    @Data
    @NoArgsConstructor
    public static class DefectMapVO {
        private double plateRatio;
        private long total;
        private long passCount;
        private long reworkCount;
        private long scrapCount;
        private List<DefectPointVO> defects = new ArrayList<>();
        private List<DefectTypeStatDTO> typeStats = new ArrayList<>();
    }

    /** 返修批次详情：批次 + 时间线 + 归并的检验记录 */
    @Data
    @NoArgsConstructor
    public static class ReworkDetailVO {
        private ReworkBatch batch;
        private List<ReworkEvent> events = new ArrayList<>();
        private List<InspectionRecord> records = new ArrayList<>();
    }

    /** 炉体分段标注 */
    @Data
    @NoArgsConstructor
    public static class StageVO {
        private String code;
        private String name;
        private double temp;
        private int seconds;
        private String hint;

        public StageVO(String code, String name, double temp, int seconds, String hint) {
            this.code = code;
            this.name = name;
            this.temp = temp;
            this.seconds = seconds;
            this.hint = hint;
        }
    }

    /** 钢化炉当炉统计 */
    @Data
    @NoArgsConstructor
    public static class FurnaceVO {
        private Long id;
        private String batchNo;
        private String furnaceNo;
        private String glassType;
        private String glassTypeName;
        private Integer thicknessMm;
        private String tempRange;
        private Integer tempLow;
        private Integer tempHigh;
        private Integer heatTemp;
        private Integer heatSeconds;
        private BigDecimal quenchPressure;
        private BigDecimal warpage;
        private Integer particleCount;
        private Integer outputCount;
        private Integer brokenCount;
        private Integer goodCount;
        private Double brokenRate;
        private String stage;
        private String stageName;
        /** 阶段已完成（终态），不可再推进 */
        private boolean finished;
        /** 出炉结果已首次确认并锁定，合格片数 / 破碎率只读 */
        private boolean resultLocked;
        /** 阶段已完成但片数缺失，允许补录一次 */
        private boolean awaitingBackfill;
        /** 出炉登记按钮是否可用 */
        private boolean canDischarge;
        /** 推进按钮是否可用 */
        private boolean canAdvance;
        /** 不可继续操作的明确原因（可操作时为 null） */
        private String lockReason;
        /** 本次操作给页面的说明（如重复出炉被保留时的提示，普通查询为 null） */
        private String notice;
        private String operator;
        private List<StageVO> stages = new ArrayList<>();
    }
}
