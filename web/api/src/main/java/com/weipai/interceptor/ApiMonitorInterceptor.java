package com.weipai.interceptor;


import com.weipai.common.Constant.STAT_ACTION;
import com.weipai.common.IPUtil;
import com.weipai.common.JacksonUtil;
import com.weipai.common.client.kafka.KafkaProperties;
import com.weipai.common.client.kafka.Producer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StopWatch;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 */
public class ApiMonitorInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ApiMonitorInterceptor.class);
    
    private ThreadLocal<StopWatch> stopWatchLocal = new ThreadLocal<StopWatch>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        StopWatch stopWatch = new StopWatch(); 
        response.setCharacterEncoding("utf-8");

        stopWatch.start();
        
        stopWatchLocal.set(stopWatch);

        return true;

    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {

    	if(HandlerMethod.class.equals(handler.getClass())){
			//获取controller，判断是不是实现登录接口的控制器
			HandlerMethod method = (HandlerMethod) handler;
			Object controller = method.getBean();
			
		}
		

    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {

    	StopWatch stopWatch = stopWatchLocal.get();
    			
    	stopWatch.stop();
    	
    	 StringBuilder b = new StringBuilder();

         Enumeration<String> em = request.getParameterNames();

         int t = 0;

         while (em.hasMoreElements()) {
             if (t++ > 0) {
                 b.append(" , ");
             }
             String name = em.nextElement();
             b.append(name).append("=").append(request.getParameter(name));
         }
         

        final long totalTimeMillis = stopWatch.getTotalTimeMillis();
        final String requestURI = request.getRequestURI();
        String content = "ip : " + IPUtil.getIP(request) + " , uri : " + requestURI + " , param : " + b.toString()+ " , time " + totalTimeMillis + " ms";

        Map map = new HashMap();
        map.put("ip", IPUtil.getIP(request));
		map.put("uri", requestURI);
		map.put("status", response.getStatus());
		map.put("action", STAT_ACTION.API_REQ.toString());
		map.put("time", totalTimeMillis);
		
		Producer.getInstance().sendData(KafkaProperties.statTopic, JacksonUtil.writeToJsonString(map));

        log.info(content);

    }
    
   

}
