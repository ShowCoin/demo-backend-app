package com.weipai.api;

import com.weipai.common.Constant;
import com.weipai.common.exception.ReturnException;
import com.weipai.interceptor.XThreadLocal;
import com.weipai.service.DeviceService;
import com.weipai.service.UserService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
@Controller
public class StatApi extends BaseApi{
	private static final Logger log = LoggerFactory.getLogger(StatApi.class);
	@Autowired
	UserService userService;
	@Autowired
	DeviceService deviceService;
	@RequestMapping("/user/stats")
	@ResponseBody
	public Map getStats(@RequestParam(required=true,value="online") String online,@RequestParam(required=true,value="play") String play){
		String uid = XThreadLocal.getInstance().getCurrentUser();
		Map<String,Object> map = new HashMap<String,Object>();
		map.put("uid", uid);
		map.put("online", online);
		map.put("play_time", play);
		map.put("action", "usage");
		//TODO 组装日志公共类，暂不发消息
		return success();
	}
	
	@RequestMapping("/user/statenter")
	@ResponseBody
	public Map statenter(){
		String uid = XThreadLocal.getInstance().getCurrentUser();
		Map<String,Object> map = new HashMap<String,Object>();
		map.put("action", "config");
		map.put("uid",uid);
		//TODO 组装日志公共类，暂不发消息
		return success();
	} 
	@RequestMapping("/user/intonewwp")
	@ResponseBody
	public Map intonewwp(@RequestParam(required=false,value="uid")String uid){
		String id = XThreadLocal.getInstance().getCurrentUser();
		Map<String,Object> map = new HashMap<String,Object>();
		map.put("action", "intoNewWp");
		map.put("uid", id);
		//TODO 组装日志公共类，暂不发消息
		return success();
	}
	
	/**
	 * 获取用户权限
	 * @param authorityType
	 * @param request
	 * @return
	 */
	@RequestMapping("get_userAuthorityState")
	@ResponseBody
	public Map getUserAuthorityState(@RequestParam(required=false,value="authorityType")String authorityType,HttpServletRequest request) throws ReturnException {
		//目前只在上传视频时会用到，所以针对原方法更改时只能兼容老版  后续新版其他地方用到时需将传过来的参数做下枚举与后端对应 该参数暂时无用
		Map<String,Object> map = new HashMap<String,Object>();
		final String uid = XThreadLocal.getInstance().getCurrentUser();
		int authorityState=1;
		if (uid == null){
			return error("2019");
		}
		if(isForbidden(uid, Constant.USER_AUTH_FORBID.UPLOAD_VIDEO.getIndex())){
			return error("5008");
		}

		try {
			String deviceId= request.getHeader("Device-Uuid");
			boolean flag=deviceService.deviceIsForbidden(deviceId);
			if(flag){
				authorityState=0;
			}
		} catch (Exception e) {
			log.info(e.getMessage(), e);
		}
		map.put("authorityState", String.valueOf(authorityState));
		return success(map);
		
	}

	/**
	 *
	 * @return
	 * @throws ServiceException
	 */
	@RequestMapping(value = "/stat/coop")
	public String coop() {
		return "redirect:http://m.imaohu.com/clientpage/third/index?appid=100310020&key=d319b9d801cf566990983caef5f5095f";
	}
	
}
