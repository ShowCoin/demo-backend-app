package com.weipai.interceptor;


import com.weipai.common.exception.ReturnException;
import com.weipai.struc.HeaderParams;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.util.Arrays;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 验证签名
 */
public class HeaderInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(HeaderInterceptor.class);
    
    private static final String HEADER_APP_NAME = "App-Name";
    private static final String HEADER_OS = "os";
    private static final String HEADER_CLIENT_VERSION = "Client-Version";
    private static final String HEADER_API_VERSION = "Api-Version";
    //经度
    private static final String HEADER_LONGITUDE = "Longitude";
    //纬度
    private static final String HEADER_LATITUDE = "Latitude";
    private static final String HEADER_DEVICE_UUID = "Device-Uuid";
    //手机类型
    private static final String HEADER_PHONE_TYPE = "Phone-Type";
    private static final String HEADER_PHONE_NUMBER = "Phone-Number";
    private static final String HEADER_CHANNEL = "Channel";
    

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception{

        
        HeaderParams headerParams = new HeaderParams();
        headerParams.setAppName(request.getHeader(HEADER_APP_NAME));

        String os = request.getHeader(HEADER_OS);
        String clientVersion = request.getHeader(HEADER_CLIENT_VERSION);
        headerParams.setOs(os);
        headerParams.setClientVersion(clientVersion);
        headerParams.setApiVersion(request.getHeader(HEADER_API_VERSION));
        headerParams.setLongitude(request.getHeader(HEADER_LONGITUDE));
        headerParams.setLatitude(request.getHeader(HEADER_LATITUDE));
        headerParams.setDeviceId(request.getHeader(HEADER_DEVICE_UUID));
        headerParams.setPhoneType(request.getHeader(HEADER_PHONE_TYPE));
        headerParams.setPhoneNum(request.getHeader(HEADER_PHONE_NUMBER));
        headerParams.setChannel(request.getHeader(HEADER_CHANNEL));
        XThreadLocal.getInstance().setHeaderParams(headerParams);

        if ("android".equals(os) && "1.4.6".compareTo(clientVersion) >0 && !Arrays.asList("/version_control", "/config", "/login", "/top_video", "/get_advertisement").contains(request.getRequestURI())) {
            throw new ReturnException("1003");
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
