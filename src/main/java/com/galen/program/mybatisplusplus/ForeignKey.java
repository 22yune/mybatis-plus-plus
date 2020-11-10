package com.galen.program.mybatisplusplus;

import java.lang.annotation.*;

/**
 * Created by baogen.zhang on 2020/3/6
 *
 * @author baogen.zhang
 * @date 2020/3/6
 */

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ForeignKey {

    String value() ;

}
