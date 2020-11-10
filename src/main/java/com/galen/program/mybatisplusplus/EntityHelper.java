package com.galen.program.mybatisplusplus;

import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.Assert;
import com.baomidou.mybatisplus.core.toolkit.ReflectionKit;

import java.lang.reflect.Field;

/**
 * Created by baogen.zhang on 2020/3/18
 *
 * @author baogen.zhang
 * @date 2020/3/18
 */
public class EntityHelper {

    public static String  column2Property(Class<?> cls,String column){
        if (null != cls) {
            TableInfo tableInfo = TableInfoHelper.getTableInfo(cls);
            Assert.notNull(tableInfo, "error: can not execute. because can not find cache of TableInfo for entity!");
            return tableInfo.getFieldList().stream().filter(fieldInfo -> fieldInfo.getColumn().equals(column) ).findFirst().get().getProperty();
        }
        return null;
    }

    public static String  column2Property(Object entity,String column){
        if (null != entity) {
            Class<?> cls = entity.getClass();
            return column2Property(cls,column);
        }
        return null;
    }

    public static void setValue(Class clazz,Object entity,String column){
        Field field = ReflectionKit.getFieldMap(clazz).get(column2Property(clazz,column));
    }
}
