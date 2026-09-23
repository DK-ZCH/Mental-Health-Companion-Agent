package com.entity.vo;

import com.entity.DictionaryEntity;
import com.baomidou.mybatisplus.annotations.TableField;
import com.baomidou.mybatisplus.annotations.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;

/**
 * 字典
 * 手机端接口返回实体辅助类
 * （主要作用去除一些不必要的字段）
 */
@TableName("sys_dict_item")
public class DictionaryVO implements Serializable {
    private static final long serialVersionUID = 1L;


    /**
     * 主键
     */

    @TableField(value = "id")
    private Long id;


    /**
     * 字段
     */

    @TableField(value = "dict_code")
    private String dictCode;


    /**
     * 字段名
     */

    @TableField(value = "dict_name")
    private String dictName;


    /**
     * 编码
     */

    @TableField(value = "item_code")
    private Integer itemCode;


    /**
     * 编码名字
     */

    @TableField(value = "item_name")
    private String itemName;


    /**
     * 父字段id
     */

    @TableField(value = "parent_id")
    private Integer parentId;


    /**
     * 备注
     */

    @TableField(value = "remark")
    private String remark;


    /**
     * 创建时间
     */
    @JsonFormat(locale="zh", timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat

    @TableField(value = "created_at")
    private Date createdAt;


    /**
	 * 设置：主键
	 */
    public Long getId() {
        return id;
    }


    /**
	 * 获取：主键
	 */

    public void setId(Long id) {
        this.id = id;
    }
    /**
	 * 设置：字段
	 */
    public String getDictCode() {
        return dictCode;
    }


    /**
	 * 获取：字段
	 */

    public void setDictCode(String dictCode) {
        this.dictCode = dictCode;
    }
    /**
	 * 设置：字段名
	 */
    public String getDictName() {
        return dictName;
    }


    /**
	 * 获取：字段名
	 */

    public void setDictName(String dictName) {
        this.dictName = dictName;
    }
    /**
	 * 设置：编码
	 */
    public Integer getItemCode() {
        return itemCode;
    }


    /**
	 * 获取：编码
	 */

    public void setItemCode(Integer itemCode) {
        this.itemCode = itemCode;
    }
    /**
	 * 设置：编码名字
	 */
    public String getItemName() {
        return itemName;
    }


    /**
	 * 获取：编码名字
	 */

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }
    /**
	 * 设置：父字段id
	 */
    public Integer getParentId() {
        return parentId;
    }


    /**
	 * 获取：父字段id
	 */

    public void setParentId(Integer parentId) {
        this.parentId = parentId;
    }
    /**
	 * 设置：备注
	 */
    public String getRemark() {
        return remark;
    }


    /**
	 * 获取：备注
	 */

    public void setRemark(String remark) {
        this.remark = remark;
    }
    /**
	 * 设置：创建时间
	 */
    public Date getCreatedAt() {
        return createdAt;
    }


    /**
	 * 获取：创建时间
	 */

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

}
