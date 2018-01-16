package com.weipai.interceptor;


import com.weipai.annotation.LoginRequired;
import com.weipai.common.DESUtil;
import com.weipai.common.TimeUtil;
import com.weipai.common.exception.ReturnException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 */
public class UserInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(UserInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws ReturnException {


        final String uid_str = request.getHeader("Weipai-Userid");
        String token = request.getHeader("Weipai-Token");


        XThreadLocal.getInstance().setCurrentUser(null);
        
        if (uid_str != null && token != null){
        	String[] arr = null;
        	try{
        		DESUtil du = new DESUtil();
            	String plain = du.decrypt(token);
            	arr = plain.split("\\|");
        	}catch(Exception e){
        		log.info("token解析失败　:"+token);
        		
        	}
        	
        	
        	if (arr != null && arr.length == 2 && arr[0].equalsIgnoreCase(uid_str)){
        		XThreadLocal.getInstance().setCurrentUser(arr[0]);
                Integer loginTime = Integer.parseInt(arr[1]);
				log.info("login time:"+TimeUtil.intDate2String(loginTime, "yyyy-MM-hh HH:mm:ss"));
				
        	}
        	
        	
        }

        HandlerMethod      method = (HandlerMethod) handler;
        LoginRequired annotationLoginRequired = method.getMethodAnnotation(LoginRequired.class);
        if (annotationLoginRequired != null && annotationLoginRequired.raiseError() && XThreadLocal.getInstance().getCurrentUser() == null) {
            throw new ReturnException("2019");
        }
        return true;

    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {

    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {

    

    }

}
