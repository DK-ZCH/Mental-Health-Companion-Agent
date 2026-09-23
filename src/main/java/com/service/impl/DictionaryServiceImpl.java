package com.service.impl;

import org.springframework.stereotype.Service;
import java.lang.reflect.Field;
import java.util.*;
import com.baomidou.mybatisplus.plugins.Page;
import com.baomidou.mybatisplus.service.impl.ServiceImpl;
import org.springframework.transaction.annotation.Transactional;
import com.utils.PageUtils;
import com.utils.Query;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import com.dao.DictionaryDao;
import com.entity.DictionaryEntity;
import com.service.DictionaryService;
import com.entity.view.DictionaryView;

/**
 * 字典 服务实现类
 */
@Service("dictionaryService")
@Transactional
public class DictionaryServiceImpl extends ServiceImpl<DictionaryDao, DictionaryEntity> implements DictionaryService {

    /**
     * 显式字典映射：编码字段 -> 字典编码。
     *
     * 说明：本表原先依靠"字段名含 Types 后缀"的反射启发式来识别待翻译字段，
     * 在数据库命名规范化（sex_types→gender、shijianduang_types→time_slot 等）后该启发式失效，
     * 因此改为显式声明映射。翻译结果写入同名的 xxxLabel 字段。
     *
     * key 格式：声明该编码字段的实体类简单名.字段名
     */
    private static final Map<String, String> DICT_CODE = new HashMap<String, String>();

    static {
        // 学生 / 心理老师 性别
        DICT_CODE.put("Yonghu.gender", "gender");
        DICT_CODE.put("Xinlilaoshi.gender", "gender");
        // 测评量表状态
        DICT_CODE.put("Exampaper.status", "assessment_paper_status");
        // 测评题目类型
        DICT_CODE.put("Examquestion.questionType", "question_type");
        // 知识分类
        DICT_CODE.put("Jiankangzhishi.category", "knowledge_category");
        // 通知分类
        DICT_CODE.put("Tongzhi.category", "notification_category");
        // 心理老师收藏类型
        DICT_CODE.put("XinlilaoshiCollection.favoriteType", "counselor_favorite_type");
        // 预约时间段与预约状态
        DICT_CODE.put("XinlilaoshiOrder.timeSlot", "time_slot");
        DICT_CODE.put("XinlilaoshiOrder.status", "appointment_status");
    }

    @Override
    public PageUtils queryPage(Map<String,Object> params) {
        if(params != null && (params.get("limit") == null || params.get("page") == null)){
            params.put("page","1");
            params.put("limit","10");
        }
        Page<DictionaryView> page = new Query<DictionaryView>(params).getPage();
        page.setRecords(baseMapper.selectListView(page,params));
        return new PageUtils(page);
    }

    /**
     * 为对象回填字典翻译字段（xxxLabel）
     *
     * @param obj     view 对象（其编码字段可能声明在父类 entity 上）
     * @param request 用于取 ServletContext 中的 dictionaryMap
     */
    public void dictionaryConvert(Object obj, HttpServletRequest request) {
        try {
            if (obj == null) return;

            ServletContext servletContext = request.getServletContext();
            @SuppressWarnings("unchecked")
            Map<String, Map<Integer, String>> dictionaryMap =
                    (Map<String, Map<Integer, String>>) servletContext.getAttribute("dictionaryMap");
            if (dictionaryMap == null) return;

            // 1. 收集对象及其所有父类中声明的 Integer 字段，并记录其声明类
            Map<String, Field>  intField  = new HashMap<String, Field>();
            Map<String, String> fieldOwner = new HashMap<String, String>();
            Class<?> c = obj.getClass();
            while (c != null && !Object.class.equals(c)) {
                for (Field f : c.getDeclaredFields()) {
                    f.setAccessible(true);
                    if ("java.lang.Integer".equals(f.getType().getName()) && !intField.containsKey(f.getName())) {
                        intField.put(f.getName(), f);
                        fieldOwner.put(f.getName(), c.getSimpleName());
                    }
                }
                c = c.getSuperclass();
            }

            // 2. 按显式映射查找字典编码，并回填 xxxLabel
            for (Map.Entry<String, Field> entry : intField.entrySet()) {
                String fieldName = entry.getKey();
                String owner     = fieldOwner.get(fieldName);
                // 字段通常声明在 XxxEntity 上，而映射键用实体语义名（去掉 Entity 后缀）
                if (owner != null && owner.endsWith("Entity")) {
                    owner = owner.substring(0, owner.length() - "Entity".length());
                }
                String dictCode  = DICT_CODE.get(owner + "." + fieldName);
                if (dictCode == null) continue;

                Field labelField = findField(obj.getClass(), fieldName + "Label");
                if (labelField == null) continue;
                labelField.setAccessible(true);

                Object raw = entry.getValue().get(obj);
                if (raw == null) {
                    labelField.set(obj, "");
                    continue;
                }
                Map<Integer, String> items = dictionaryMap.get(dictCode);
                String label = (items != null) ? items.get(raw) : null;
                labelField.set(obj, label != null ? label : "");
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 在类及其父类中查找字段
     */
    private Field findField(Class<?> c, String fieldName) {
        while (c != null && !Object.class.equals(c)) {
            for (Field f : c.getDeclaredFields()) {
                if (fieldName.equals(f.getName())) return f;
            }
            c = c.getSuperclass();
        }
        return null;
    }

    /**
     * 判断本实体有没有这个字段
     */
    public boolean hasField(Class<?> c, String fieldName){
        Field[] fields = c.getDeclaredFields();
        for (Field f : fields) {
            if (fieldName.equals(f.getName())) {
                return true;
            }
        }
        return false;
    }

}
