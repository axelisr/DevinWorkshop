package com.piggymetrics.statistics.repository;

import com.piggymetrics.statistics.domain.timeseries.DataPoint;
import com.piggymetrics.statistics.domain.timeseries.DataPointId;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DataPointRepository extends CrudRepository<DataPoint, DataPointId> {

	// DataPointId is mapped by custom converters (no PersistentEntity), so the id.account path cannot be derived
	@Query("{ '_id.account' : ?0 }")
	List<DataPoint> findByIdAccount(String account);

}
