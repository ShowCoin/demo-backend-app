package com.weipai.interceptor;


import com.weipai.common.Constant.USER_AGENT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 获取agent
 */
public class AgentInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AgentInterceptor.class);



    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception{

    	String agent = request.getHeader("User-Agent") == null ? "" : request.getHeader("User-Agent");
    	agent = agent.toLowerCase();

    	USER_AGENT userAgent = null;
        if (agent.indexOf("weipai light") != -1) {
            userAgent = USER_AGENT.ANDROID_LIGHT;
        }
    	else if (agent.indexOf("android") != -1 || agent.indexOf("apache-httpclient") != -1|| agent.contains("okhttp")) {
    		userAgent = USER_AGENT.ANDROID;
		}else if(agent.indexOf( "iphone" ) >  0 || agent.indexOf( "ipad" ) >  0 || agent.indexOf( "ipod" ) >  0 || agent.indexOf( "ios" ) >  0){
			userAgent = USER_AGENT.IOS;
		}else{
			userAgent = USER_AGENT.PC;
		}
    	XThreadLocal.getInstance().setUserAgent(userAgent);
        return true;

    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {

    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {



    }

}
