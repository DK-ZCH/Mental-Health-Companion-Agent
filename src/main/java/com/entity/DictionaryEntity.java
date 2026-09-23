package com.entity;

import com.baomidou.mybatisplus.annotations.TableId;
import com.baomidou.mybatisplus.annotations.TableName;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.lang.reflect.InvocationTargetException;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.beanutils.BeanUtils;
import com.baomidou.mybatisplus.annotations.TableField;
import com.baomidou.mybatisplus.enums.FieldFill;
import com.baomidou.mybatisplus.enums.IdType;

/**
 * 字典
 *
 * @author 
 * @email
 */
@TableName("sys_dict_item")
public class DictionaryEntity<T> implements Serializable {
    private static final long serialVersionUID = 1L;


	public DictionaryEntity() {

	}

	public DictionaryEntity(T t) {
		try {
			BeanUtils.copyProperties(this, t);
		} catch (IllegalAccessException | InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}


    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
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
    @TableField(value = "created_at",fill = FieldFill.INSERT)

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

    @Override
    public String toString() {
        return "Dictionary{" +
            "id=" + id +
            ", dictCode=" + dictCode +
            ", dictName=" + dictName +
            ", itemCode=" + itemCode +
            ", itemName=" + itemName +
            ", parentId=" + parentId +
            ", remark=" + remark +
            ", createdAt=" + createdAt +
        "}";
    }
}
