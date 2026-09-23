package com.entity.model;

import com.entity.DictionaryEntity;

import com.baomidou.mybatisplus.annotations.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import org.springframework.format.annotation.DateTimeFormat;
import java.io.Serializable;


/**
 * 字典
 * 接收传参的实体类
 *（实际开发中配合移动端接口开发手动去掉些没用的字段， 后端一般用entity就够用了）
 * 取自ModelAndView 的model名称
 */
public class DictionaryModel implements Serializable {
    private static final long serialVersionUID = 1L;




    /**
     * 主键
     */
    private Long id;


    /**
     * 字段
     */
    private String dictCode;


    /**
     * 字段名
     */
    private String dictName;


    /**
     * 编码
     */
    private Integer itemCode;


    /**
     * 编码名字
     */
    private String itemName;


    /**
     * 父字段id
     */
    private Integer parentId;


    /**
     * 备注
     */
    private String remark;


    /**
     * 创建时间
     */
    @JsonFormat(locale="zh", timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat
    private Date createdAt;


    /**
	 * 获取：主键
	 */
    public Long getId() {
        return id;
    }


    /**
	 * 设置：主键
	 */
    public void setId(Long id) {
        this.id = id;
    }
    /**
	 * 获取：字段
	 */
    public String getDictCode() {
        return dictCode;
    }


    /**
	 * 设置：字段
	 */
    public void setDictCode(String dictCode) {
        this.dictCode = dictCode;
    }
    /**
	 * 获取：字段名
	 */
    public String getDictName() {
        return dictName;
    }


    /**
	 * 设置：字段名
	 */
    public void setDictName(String dictName) {
        this.dictName = dictName;
    }
    /**
	 * 获取：编码
	 */
    public Integer getItemCode() {
        return itemCode;
    }


    /**
	 * 设置：编码
	 */
    public void setItemCode(Integer itemCode) {
        this.itemCode = itemCode;
    }
    /**
	 * 获取：编码名字
	 */
    public String getItemName() {
        return itemName;
    }


    /**
	 * 设置：编码名字
	 */
    public void setItemName(String itemName) {
        this.itemName = itemName;
    }
    /**
	 * 获取：父字段id
	 */
    public Integer getParentId() {
        return parentId;
    }


    /**
	 * 设置：父字段id
	 */
    public void setParentId(Integer parentId) {
        this.parentId = parentId;
    }
    /**
	 * 获取：备注
	 */
    public String getRemark() {
        return remark;
    }


    /**
	 * 设置：备注
	 */
    public void setRemark(String remark) {
        this.remark = remark;
    }
    /**
	 * 获取：创建时间
	 */
    public Date getCreatedAt() {
        return createdAt;
    }


    /**
	 * 设置：创建时间
	 */
    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    }
