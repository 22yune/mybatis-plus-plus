package com.galen.program.mybatisplusplus;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.Assert;
import com.baomidou.mybatisplus.core.toolkit.ReflectionKit;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.Data;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.ResolvableType;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

/**
 * 支持带子表的CUD操作
 * <p>
 * Created by baogen.zhang on 2020/3/5
 *
 * @author baogen.zhang
 * @date 2020/3/5
 */

//TODO 支持当前线程的上下文的配置。根据配置指定对应调用模式。不同模式使用命令模式?
@Transactional
public class ExtServiceImpl<M extends BaseMapper<T>, T> extends ServiceImpl<M, T> implements
        IExtService<T>,InitializingBean, ApplicationContextAware {
    /**
     * 子表注解外键字段信息列表
     */
    protected List<ForeignKeyInfo> foreignKeyInfoList = new ArrayList<>();


    protected ApplicationContext applicationContext;

    @Data
    private static class ForeignKeyInfo {
        /**
         * 标记了外键的子表集合字段
         */
        private Field field;
        /**
         * 被作为外键的子表entity类型
         */
        private Class clazz;
        /**
         * 被作为外键的子表entity类型的service
         */
        private IService service;
        /**
         * 被作为外键的子表entity类型的外键列名
         */
        private String column;
        /**
         * 子表对应的外键字段
         */
        private Field foreignField;

        public ForeignKeyInfo(Field field, Class clazz, String column, IService service ) {
            this.field = field;
            this.clazz = clazz;
            this.column = column;
            this.service = service;
        }

        public Field  getForeignField(){
            if(this.foreignField == null){
                synchronized (this){
                    if(this.foreignField == null){
                        Field foreignField = ReflectionKit.getFieldMap(clazz).get(EntityHelper.column2Property(clazz,column));
                        foreignField.setAccessible(true);
                        this.foreignField = foreignField;
                    }
                }
            }
            return foreignField;
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Class<T> tClass = currentModelClass();
        List<Field> list = TableInfoHelper.getAllFields(tClass);
        for (Field field : list) {
            ForeignKey foreignKey = field.getAnnotation(ForeignKey.class);
            if (foreignKey != null) {
                if (Collection.class.isAssignableFrom(field.getType())) {
                    Type type = field.getGenericType();
                    if (type instanceof ParameterizedType) {
                        Type[] typeArray = ((ParameterizedType) type).getActualTypeArguments();
                        Class<?> clazz = (Class<?>) typeArray[0];
                        field.setAccessible(true);
                        foreignKeyInfoList.add(new ForeignKeyInfo(field,clazz,foreignKey.value(),getService(clazz)));
                    } else {
                        throw new RuntimeException("@ForeignKey注解的字段必须是带有泛型参数的集合类型");
                    }
                } else {
                    throw new RuntimeException("@ForeignKey注解的字段必须是集合类型");
                }
            }
        }
    }

    protected <I> IService<I> getService(Class<I> iClass) {
        Map<String, IService> map = applicationContext.getBeansOfType(IService.class);
        List<IService<I>> list = new ArrayList<>();
        map.forEach((k, v) -> {
            if (ResolvableType.forClassWithGenerics(IService.class, iClass).isAssignableFrom(v.getClass())) {
                list.add(v);
            }
        });
        if (list.size() == 0) {
            throw new RuntimeException(String.format("未找到%s的Service", iClass.getName()));
        } else if (list.size() > 1) {
            throw new RuntimeException(String.format("找到多个%s的Service%s", iClass.getName(), list.toArray().toString()));
        }
        return list.get(0);
    }

    protected Serializable getId(T entity) {
        if (null != entity) {
            Class<?> cls = entity.getClass();
            TableInfo tableInfo = TableInfoHelper.getTableInfo(cls);
            Assert.notNull(tableInfo, "error: can not execute. because can not find cache of TableInfo for entity!");
            String keyProperty = tableInfo.getKeyProperty();
            Assert.notEmpty(keyProperty, "error: can not execute. because can not find column for id from entity!");
            Object idVal = ReflectionKit.getMethodValue(cls, entity, tableInfo.getKeyProperty());
            return (Serializable) idVal;
        }
        return null;
    }

    protected Collection<Serializable> toIdList(Collection<T> entityList) {
        List list = new ArrayList();
        for (T entity : entityList) {
            list.add(getId(entity));
        }
        return list;
    }

    protected void saveChildBatch(Collection<T> entityList) {
        foreignKeyInfoList.forEach( foreignKeyInfo -> {
            Collection collection = null;
            for (T e : entityList) {
                try {
                    Collection col = (Collection<?>) foreignKeyInfo.field.get(e);
                    if(col != null){
                        Object id = getId(e);
                        col.forEach(c -> {
                            //  设置外键
                            try {
                                foreignKeyInfo.getForeignField().set(c,id);
                            } catch (IllegalAccessException e1) {
                                e1.printStackTrace();
                            }
                        });
                        if (collection == null) {
                            collection = col;
                        } else {
                            collection.addAll(col);
                        }
                    }
                } catch (IllegalAccessException e1) {
                    e1.printStackTrace();
                }
            }
            foreignKeyInfo.service.saveBatch(collection);
        });
    }

    protected void removeChildBatch(Collection<? extends Serializable> idList) {
        foreignKeyInfoList.forEach( foreignKeyInfo -> {
            foreignKeyInfo.service.remove((Wrapper) new QueryWrapper().in(foreignKeyInfo.column, idList));
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(T entity) {
        boolean result = super.save(entity);
        saveChildBatch(Arrays.asList(entity));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveBatch(Collection<T> entityList, int batchSize) {
        boolean result = super.saveBatch(entityList, batchSize);
        saveChildBatch(entityList);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveOrUpdate(T entity) {
        boolean save = true;
        Object idVal = null;
        if (null != entity) {
            idVal = getId(entity);
            save = StringUtils.checkValNull(idVal) || Objects.isNull(getById((Serializable) idVal));
        }
        boolean result;
        if (save) {
            result = save(entity);
        } else {
            result = updateById(entity);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveOrUpdateBatch(Collection<T> entityList, int batchSize) {
        boolean result = super.saveOrUpdateBatch(entityList, batchSize);
        removeChildBatch(toIdList(entityList));
        saveChildBatch(entityList);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(Serializable id) {
        boolean result = super.removeById(id);
        removeChildBatch(Arrays.asList(id));
        return result;
    }

    /*@Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeByMap(Map<String, Object> columnMap) {
        Collection<T> entityList = super.listByMap(columnMap);
        return removeByIds(toIdList(entityList));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Wrapper<T> wrapper) {
        Collection<T> entityList = super.list(wrapper);
        return removeByIds(toIdList(entityList));
    }*/

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeByIds(Collection<? extends Serializable> idList) {
        boolean result = super.removeByIds(idList);
        removeChildBatch(idList);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(T entity) {
        boolean result = super.updateById(entity);
        removeChildBatch(Arrays.asList(getId(entity)));
        saveChildBatch(Arrays.asList(entity));
        return result;
    }
    /*
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(T entity, Wrapper<T> updateWrapper) {
        return super.update(entity, updateWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateBatchById(Collection<T> entityList, int batchSize) {
        //   return super.updateBatchById(entityList, batchSize);
        boolean result = super.updateBatchById(entityList, batchSize);
        removeChildBatch(toIdList(entityList));
        saveChildBatch(entityList);
        return result;
    }*/

}
