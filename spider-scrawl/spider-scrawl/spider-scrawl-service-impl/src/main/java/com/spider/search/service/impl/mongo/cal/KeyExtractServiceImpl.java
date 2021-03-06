package com.spider.search.service.impl.mongo.cal;

import com.mongodb.client.MongoDatabase;
import com.spider.base.mongo.MongoConnUtil;
import com.spider.search.service.api.mongo.*;
import com.spider.search.service.dto.DocQueue;
import com.spider.search.service.enums.SpiderNodeEnum;
import com.spider.search.service.impl.mongo.AbstractSpiderBaseService;
import com.spider.search.service.impl.mongo.thread.KeyExtractThread;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
public class KeyExtractServiceImpl extends AbstractSpiderBaseService implements KeyExtractService {

    private final static Logger logger = LoggerFactory.getLogger(KeyExtractServiceImpl.class);

    @Autowired
    private InputDataService fundInputDataService;
    @Autowired
    private FlowService flowService;
    @Autowired
    private KeyExtractNodeService keyExtractNodeService;
    @Autowired
    private KeyWordsService fundKeyWordsService;
    @Autowired
    private AuditService auditService;

    @Override
	public void cal(){
        MongoConnUtil mongoConnUtil = new MongoConnUtil();
        MongoDatabase mongoDatabase = mongoConnUtil.initConn();

        fundInputDataService.setDatabase(mongoDatabase);
        flowService.setDatabase(mongoDatabase);
        keyExtractNodeService.setDatabase(mongoDatabase);
        fundKeyWordsService.setDatabase(mongoDatabase);
        auditService.setDatabase(mongoDatabase);

        //	线程池核心数
        Integer corePoolSize = 1;
        //	线程池最大数
        Integer maxPoolSize = 1;
		//	队列最大数
        Integer maxQueueSize = 2;
        //	线程池执行器
        ThreadPoolExecutor executor;
        //	工作队列
        DocQueue workQueue = new DocQueue();
		//	线程池定义
		LinkedBlockingDeque<Runnable> excutorQueue = new LinkedBlockingDeque<Runnable>(maxPoolSize);
		executor = new ThreadPoolExecutor(corePoolSize, maxPoolSize,60L, TimeUnit.SECONDS, excutorQueue);
		workQueue.setQueueSize(maxQueueSize);
		int icount = 0;
		try {
            while (true) {
                Thread.sleep(5000);
                if ((workQueue.size() > maxPoolSize && executor.getActiveCount() < maxPoolSize)
                        || (workQueue.size() > 0 && executor.getActiveCount() < corePoolSize)) {
                    executor.submit(new KeyExtractThread(workQueue, "thead-" + icount++, mongoDatabase, fundInputDataService, fundKeyWordsService, keyExtractNodeService, auditService));
                } else if (workQueue.size() <= maxQueueSize / 2) {
                    try {
                        Document doc = new Document();
                        doc.put("nodeCode", SpiderNodeEnum.KEYEXTRACT.getValue());
                        doc.put("startFlag", "0");
                        List<Document> list = flowService.findList(doc);
                        if(null != list){
                            for(int jcount=0; jcount<list.size(); jcount++){
                                if(workQueue.getQueueSize() < maxQueueSize){
                                    Document doc01 = list.get(jcount);
                                    String urlId = String.valueOf(doc01.get("urlId"));
                                    keyExtractNodeService.startFlow(urlId);
                                    Document doc02 = new Document();
                                    doc02.put("urlId", urlId);
                                    workQueue.add(doc02);
                                } else {
                                    break;
                                }
                            }
                        }else{
                            Thread.sleep(10000);
                        }
                    } catch (Exception e) {
                        logger.info("异常信息 e:{}", ExceptionUtils.getStackTrace(e));
                    }
                }
            }
        }catch (Exception e){
            logger.info("异常信息 e:{}", ExceptionUtils.getStackTrace(e));
        }finally {
            mongoConnUtil.connClose();
        }
    }
}



