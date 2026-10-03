package com.glass.works.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 切割相关入参 / 出参。合并在一个文件里，避免过多细碎类文件。 */
public final class CuttingRequests {

    private CuttingRequests() {
    }

    /** 一条出片规格 */
    @Data
    @NoArgsConstructor
    public static class PieceRequest {
        private Integer lengthMm;
        private Integer widthMm;
        private Integer quantity;
        private String remark;

        public PieceRequest(Integer lengthMm, Integer widthMm, Integer quantity) {
            this.lengthMm = lengthMm;
            this.widthMm = widthMm;
            this.quantity = quantity;
        }
    }

    /** 生成切割单入参 */
    @Data
    @NoArgsConstructor
    public static class OrderRequest {
        private Long stockId;
        private String layoutNo;
        private String cutter;
        private String operator;
        private List<PieceRequest> pieces = new ArrayList<>();
    }

    /** 试算入参（不落库） */
    @Data
    @NoArgsConstructor
    public static class PreviewRequest {
        private Long stockId;
        private Integer sourceLengthMm;
        private Integer sourceWidthMm;
        private List<PieceRequest> pieces = new ArrayList<>();
    }

    /** 布局图上的一个矩形（单位 mm，原点左上） */
    @Data
    @NoArgsConstructor
    public static class Rect {
        private double x;
        private double y;
        private double w;
        private double h;
        /** PIECE 出片 / REMNANT 余料 */
        private String type;
        private String label;
        private Integer quantity;

        public Rect(double x, double y, double w, double h, String type, String label, Integer quantity) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.type = type;
            this.label = label;
            this.quantity = quantity;
        }
    }

    /** 试算出参：利用率、余料、布局矩形 */
    @Data
    @NoArgsConstructor
    public static class PreviewResult {
        private String batchNo;
        private String glassType;
        private Integer sourceLengthMm;
        private Integer sourceWidthMm;
        private double sourceAreaSqm;
        private double outputAreaSqm;
        private double remnantAreaSqm;
        private double utilizationRate;
        private String utilizationLevel;
        private String remnantSpec;
        private int outputCount;
        private boolean overflow;
        private String message;
        private List<Rect> layout = new ArrayList<>();
        private List<PieceRequest> pieces = new ArrayList<>();
    }
}
