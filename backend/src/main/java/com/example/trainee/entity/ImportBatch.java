package com.example.trainee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("import_batch")
public class ImportBatch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String type;
    @TableField("batch_no")
    private String batchNo;
    @TableField("total_rows")
    private Integer totalRows;
    private Integer imported;
    private Integer skipped;
    @TableField("strict_mode")
    private Integer strictMode;
    private LocalDateTime createTime;
}
