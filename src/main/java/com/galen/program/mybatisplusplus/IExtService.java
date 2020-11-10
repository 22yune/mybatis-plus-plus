package com.galen.program.mybatisplusplus;

import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.transaction.annotation.Transactional;

/**
 * Created by baogen.zhang on 2020/3/6
 *
 * @author baogen.zhang
 * @date 2020/3/6
 */
@Transactional(rollbackFor = Exception.class)
public interface IExtService<T> extends IService<T> {

    /*public boolean saveWC(T entity);
    public boolean saveBatchWC(Collection<T> entityList, int batchSize);
    public boolean saveOrUpdateWC(T entity);
    public boolean saveOrUpdateBatchWC(Collection<T> entityList, int batchSize);
    public boolean removeByIdWC(Serializable id);
    public boolean removeByMapWC(Map<String, Object> columnMap);
    public boolean removeWC(Wrapper<T> wrapper);
    public boolean removeByIdsWC(Collection<? extends Serializable> idList);
    public boolean updateByIdWC(T entity);
    public boolean updateBatchByIdWC(Collection<T> entityList, int batchSize);*/
}
