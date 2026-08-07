package com.fitmatch.repository;

import com.fitmatch.entity.GeocodeCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

public interface GeocodeCacheRepository extends JpaRepository<GeocodeCache, String> {

    /**
     * Xoá bản ghi quá hạn — gọi từ job backfill để bảng không phình vô hạn.
     *
     * <p>{@code @Transactional} nằm ở đây chứ không ở caller: job gọi thẳng
     * phương thức của chính nó thì không đi qua proxy Spring, nên annotation đặt
     * bên đó sẽ không có tác dụng và câu delete ném TransactionRequiredException.
     */
    @Modifying
    @Transactional
    @Query("delete from GeocodeCache c where c.cachedAt < :before")
    int deleteByCachedAtBefore(@Param("before") LocalDateTime before);
}
