/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

 package org.apache.ranger.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.Metamodel;
import javax.persistence.metamodel.SingularAttribute;

import org.apache.commons.lang.StringUtils;
import org.apache.ranger.common.SearchCriteria;
import org.apache.ranger.common.SearchField;
import org.apache.ranger.common.SortField;
import org.apache.ranger.common.SortField.SORT_ORDER;
import org.apache.ranger.db.RangerDaoManager;
import org.apache.ranger.entity.XXPortalUser;
import org.apache.ranger.entity.XXTrxLog;
import org.apache.ranger.entity.view.VXXTrxLog;
import org.apache.ranger.view.VXTrxLog;
import org.apache.ranger.view.VXTrxLogList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
@Scope("singleton")
public class XTrxLogService extends XTrxLogServiceBase<XXTrxLog, VXTrxLog> {
	@Autowired
	RangerDaoManager rangerDaoManager;
	public XTrxLogService(){
		searchFields.add(new SearchField("attributeName", "obj.attributeName",
				SearchField.DATA_TYPE.STRING, SearchField.SEARCH_TYPE.PARTIAL));
		searchFields.add(new SearchField("action", "obj.action",
				SearchField.DATA_TYPE.STRING, SearchField.SEARCH_TYPE.PARTIAL));
		searchFields.add(new SearchField("sessionId", "obj.sessionId",
				SearchField.DATA_TYPE.STRING, SearchField.SEARCH_TYPE.FULL));
		searchFields.add(new SearchField("startDate", "obj.createTime",
				SearchField.DATA_TYPE.DATE, SearchField.SEARCH_TYPE.GREATER_EQUAL_THAN));	
		searchFields.add(new SearchField("endDate", "obj.createTime",
				SearchField.DATA_TYPE.DATE, SearchField.SEARCH_TYPE.LESS_EQUAL_THAN));	
		searchFields.add(new SearchField("owner", "obj.addedByUserId",
				SearchField.DATA_TYPE.INT_LIST, SearchField.SEARCH_TYPE.FULL));
		searchFields.add(new SearchField("objectClassType", "obj.objectClassType",
				SearchField.DATA_TYPE.INT_LIST, SearchField.SEARCH_TYPE.FULL));
		
		sortFields.add(new SortField("createDate", "obj.createTime", true, SORT_ORDER.DESC));
		}

	@Override
	protected void validateForCreate(VXTrxLog vObj) {}

	@Override
	protected void validateForUpdate(VXTrxLog vObj, XXTrxLog mObj) {}

	@Override
	public VXTrxLogList searchXTrxLogs(SearchCriteria searchCriteria) {		
		EntityManager em = daoMgr.getEntityManager();
		CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
		CriteriaQuery<VXXTrxLog> selectCQ = criteriaBuilder.createQuery(VXXTrxLog.class);
		Root<VXXTrxLog> rootEntityType = selectCQ.from(VXXTrxLog.class);
		Predicate predicate = generatePredicate(searchCriteria, em, criteriaBuilder, rootEntityType);

		selectCQ.where(predicate);
		if("asc".equalsIgnoreCase(searchCriteria.getSortType())){
			selectCQ.orderBy(criteriaBuilder.asc(rootEntityType.get("createTime")));
		}else{
			selectCQ.orderBy(criteriaBuilder.desc(rootEntityType.get("createTime")));
		}
		int startIndex = searchCriteria.getStartIndex();
		int pageSize = searchCriteria.getMaxRows();
		List<VXXTrxLog> resultList = em.createQuery(selectCQ).setFirstResult(startIndex).setMaxResults(pageSize).getResultList();

		List<VXTrxLog> trxLogList = new ArrayList<VXTrxLog>();
		for(VXXTrxLog xTrxLog : resultList){
			VXTrxLog trxLog = mapCustomViewToViewObj(xTrxLog);

			if(trxLog.getUpdatedBy() != null){
				XXPortalUser xXPortalUser= rangerDaoManager.getXXPortalUser().getById(
						Long.parseLong(trxLog.getUpdatedBy()));
				if(xXPortalUser != null){
					trxLog.setOwner(xXPortalUser.getLoginId());
				}
			}

			trxLogList.add(trxLog);
		}

		VXTrxLogList vxTrxLogList = new VXTrxLogList();
		vxTrxLogList.setStartIndex(startIndex);
		vxTrxLogList.setPageSize(pageSize);
		vxTrxLogList.setVXTrxLogs(trxLogList);
		return vxTrxLogList;
	}

	public Long searchXTrxLogsCount(SearchCriteria searchCriteria) {
		EntityManager em = daoMgr.getEntityManager();
		CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
		CriteriaQuery<VXXTrxLog> selectCQ = criteriaBuilder.createQuery(VXXTrxLog.class);
		Root<VXXTrxLog> rootEntityType = selectCQ.from(VXXTrxLog.class);
		Predicate predicate = generatePredicate(searchCriteria, em, criteriaBuilder, rootEntityType);

		CriteriaQuery<Long> countCQ = criteriaBuilder.createQuery(Long.class);
		countCQ.select(criteriaBuilder.count(rootEntityType)).where(predicate);
		List<Long> countList = em.createQuery(countCQ).getResultList();
		Long count = 0L;
		if(!CollectionUtils.isEmpty(countList)) {
			count = countList.get(0);
			if(count == null) {
				count = 0L;
			}
		}
		return count;
	}

	private Predicate generatePredicate(SearchCriteria searchCriteria, EntityManager em,
			CriteriaBuilder criteriaBuilder, Root<VXXTrxLog> rootEntityType) {
		Predicate predicate = criteriaBuilder.conjunction();
		Map<String, Object> paramList = searchCriteria.getParamList();
		if (CollectionUtils.isEmpty(paramList)) {
			return predicate;
		}

		Metamodel entityMetaModel = em.getMetamodel();
		EntityType<VXXTrxLog> entityType = entityMetaModel.entity(VXXTrxLog.class);

		for (Map.Entry<String, Object> entry : paramList.entrySet()) {
			String key=entry.getKey();
			for (SearchField searchField : searchFields) {
				if (!key.equalsIgnoreCase(searchField.getClientFieldName())) {
					continue;
				}

				String fieldName = searchField.getFieldName();
				if (!StringUtils.isEmpty(fieldName)) {
					fieldName = fieldName.contains(".") ? fieldName.substring(fieldName.indexOf(".") + 1) : fieldName;
				}

				Object paramValue = entry.getValue();
				boolean isListValue = false;
				if (paramValue != null && paramValue instanceof Collection) {
					isListValue = true;
				}

				// build where clause depending upon given parameters
				if (SearchField.DATA_TYPE.STRING.equals(searchField.getDataType())) {
					// build where clause for String datatypes
					SingularAttribute attr = entityType.getSingularAttribute(fieldName);
					if (attr != null) {
						Predicate stringPredicate = null;
						if (SearchField.SEARCH_TYPE.PARTIAL.equals(searchField.getSearchType())) {
							String val = "%" + paramValue + "%";
							stringPredicate = criteriaBuilder.like(rootEntityType.get(attr), val);
						} else {
							stringPredicate = criteriaBuilder.equal(rootEntityType.get(attr), paramValue);
						}
						predicate = criteriaBuilder.and(predicate, stringPredicate);
					}

				} else if (SearchField.DATA_TYPE.INT_LIST.equals(searchField.getDataType()) || isListValue
						&& SearchField.DATA_TYPE.INTEGER.equals(searchField.getDataType())) {
					// build where clause for integer lists or integers datatypes
					Collection<Number> intValueList = null;
					if (paramValue != null && (paramValue instanceof Integer || paramValue instanceof Long)) {
						intValueList = new ArrayList<Number>();
						intValueList.add((Number) paramValue);
					} else {
						intValueList = (Collection<Number>) paramValue;
					}
					for (Number value : intValueList) {
						SingularAttribute attr = entityType.getSingularAttribute(fieldName);
						if (attr != null) {
							Predicate intPredicate = criteriaBuilder.equal(rootEntityType.get(attr), value);
							predicate = criteriaBuilder.and(predicate, intPredicate);
						}
					}

				} else if (SearchField.DATA_TYPE.DATE.equals(searchField.getDataType())) {
					// build where clause for date datatypes
					Date fieldValue = (Date) paramList.get(searchField.getClientFieldName());
					if (fieldValue != null && searchField.getCustomCondition() == null) {
						SingularAttribute attr = entityType.getSingularAttribute(fieldName);
						Predicate datePredicate = null;
						if (SearchField.SEARCH_TYPE.LESS_THAN.equals(searchField.getSearchType())) {
							datePredicate = criteriaBuilder.lessThan(rootEntityType.get(attr), fieldValue);
						} else if (SearchField.SEARCH_TYPE.LESS_EQUAL_THAN.equals(searchField.getSearchType())) {
							datePredicate = criteriaBuilder.lessThanOrEqualTo(rootEntityType.get(attr), fieldValue);
						} else if (SearchField.SEARCH_TYPE.GREATER_THAN.equals(searchField.getSearchType())) {
							datePredicate = criteriaBuilder.greaterThan(rootEntityType.get(attr), fieldValue);
						} else if (SearchField.SEARCH_TYPE.GREATER_EQUAL_THAN.equals(searchField.getSearchType())) {
							datePredicate = criteriaBuilder.greaterThanOrEqualTo(rootEntityType.get(attr), fieldValue);
						} else {
							datePredicate = criteriaBuilder.equal(rootEntityType.get(attr), fieldValue);
						}
						predicate = criteriaBuilder.and(predicate, datePredicate);
					}
				}
			}
		}
		return predicate;
	}
	
	private VXTrxLog mapCustomViewToViewObj(VXXTrxLog vXXTrxLog){
		VXTrxLog vXTrxLog = new VXTrxLog();
		vXTrxLog.setId(vXXTrxLog.getId());
		vXTrxLog.setAction(vXXTrxLog.getAction());
		vXTrxLog.setAttributeName(vXXTrxLog.getAttributeName());
		vXTrxLog.setCreateDate(vXXTrxLog.getCreateTime());
		vXTrxLog.setNewValue(vXXTrxLog.getNewValue());
		vXTrxLog.setPreviousValue(vXXTrxLog.getPreviousValue());
		vXTrxLog.setSessionId(vXXTrxLog.getSessionId());
		if(vXXTrxLog.getUpdatedByUserId()==null || vXXTrxLog.getUpdatedByUserId()==0){
			vXTrxLog.setUpdatedBy(null);
		}else{
			vXTrxLog.setUpdatedBy(String.valueOf(vXXTrxLog.getUpdatedByUserId()));
		}
		//We will have to get this from XXUser
		//vXTrxLog.setOwner(vXXTrxLog.getAddedByUserName());
		vXTrxLog.setParentObjectClassType(vXXTrxLog.getParentObjectClassType());
		vXTrxLog.setParentObjectName(vXXTrxLog.getParentObjectName());
		vXTrxLog.setObjectClassType(vXXTrxLog.getObjectClassType());
		vXTrxLog.setObjectId(vXXTrxLog.getObjectId());
		vXTrxLog.setObjectName(vXXTrxLog.getObjectName());
		vXTrxLog.setTransactionId(vXXTrxLog.getTransactionId());
		return vXTrxLog;
	}
	
	@Override
	protected XXTrxLog mapViewToEntityBean(VXTrxLog vObj, XXTrxLog mObj, int OPERATION_CONTEXT) {
		if(vObj!=null && mObj!=null){
			super.mapViewToEntityBean(vObj, mObj, OPERATION_CONTEXT);
			XXPortalUser xXPortalUser=null;
			if(mObj.getAddedByUserId()==null || mObj.getAddedByUserId()==0){
				if(!stringUtil.isEmpty(vObj.getOwner())){
					xXPortalUser=rangerDaoManager.getXXPortalUser().findByLoginId(vObj.getOwner());	
					if(xXPortalUser!=null){
						mObj.setAddedByUserId(xXPortalUser.getId());
					}
				}
			}
			if(mObj.getUpdatedByUserId()==null || mObj.getUpdatedByUserId()==0){
				if(!stringUtil.isEmpty(vObj.getUpdatedBy())){
					xXPortalUser= rangerDaoManager.getXXPortalUser().findByLoginId(vObj.getUpdatedBy());			
					if(xXPortalUser!=null){
						mObj.setUpdatedByUserId(xXPortalUser.getId());
					}		
				}
			}
		}
		return mObj;
	}

	@Override
	protected VXTrxLog mapEntityToViewBean(VXTrxLog vObj, XXTrxLog mObj) {
        if(mObj!=null && vObj!=null){
            super.mapEntityToViewBean(vObj, mObj);
            XXPortalUser xXPortalUser=null;
            if(stringUtil.isEmpty(vObj.getOwner())){
                xXPortalUser= rangerDaoManager.getXXPortalUser().getById(mObj.getAddedByUserId());
                if(xXPortalUser!=null){
                    vObj.setOwner(xXPortalUser.getLoginId());
                }
            }
            if(stringUtil.isEmpty(vObj.getUpdatedBy())){
                xXPortalUser= rangerDaoManager.getXXPortalUser().getById(mObj.getUpdatedByUserId());
                if(xXPortalUser!=null){
                    vObj.setUpdatedBy(xXPortalUser.getLoginId());
                }
            }
        }
        return vObj;
	}
}
