package com.weipai.api;

import com.weipai.common.JacksonUtil;
import com.weipai.common.ReflectionUtils;
import com.weipai.common.cache.LocalCache;
import com.weipai.common.exception.ReturnException;
import com.weipai.interceptor.XThreadLocal;
import com.weipai.service.DeviceService;
import com.weipai.service.SettingService;
import com.weipai.struc.SettingParam;
import com.weipai.user.thrift.view.SettingView;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class SettingApi extends BaseApi {

	@Autowired
	private SettingService settingService;

	@Autowired 
	private DeviceService deviceService;
	
	@RequestMapping(value = "/set_switch")
	@SuppressWarnings("unchecked")
	public Map setSwitch(
			@RequestParam(required = false, value = "weipai_userid") String user_id,
			@RequestParam(required = true, value = "switches_status") String switchesStatus) throws ReturnException {

		String uid = XThreadLocal.getInstance().getCurrentUser();
		if (uid == null) {
			return error("2019");
		}
		
		Map<String, String> map = new HashMap<String, String>();
		Map<String, Object> dataMap = new HashMap<String, Object>();
		
		Map<String, Object> resultMap= new HashMap<String, Object>();
		if (switchesStatus.startsWith("[") && switchesStatus.endsWith("]")) {
			switchesStatus = switchesStatus.substring(1,
					switchesStatus.length() - 1);
		}
		map = JacksonUtil.readJsonToObject(map.getClass(), switchesStatus);
		String key = map.get("key");
		String updateKey = "";

		if (key != null && key.length() > 7) {
			// 参数去掉前缀
			updateKey = key.substring(7, key.length());
			// 转换驼峰式命名
			// key = replaceUnderlineAndfirstToUpper(key, "_", "");
		} else {
			return error("1002");
		}
		String updateValue = map.get("value");
		Map<String, String> paramMap = new HashMap<String, String>();
		paramMap.put(updateKey, updateValue);
		try {
			settingService.setSwitch(uid,paramMap);
		} catch (Exception e) {
			e.printStackTrace();
			return error("0");
		}
		dataMap.put("key", key);
		dataMap.put("value", updateValue);
		resultMap.put("switches_status", dataMap);
		return success(resultMap);
	}
	
	@RequestMapping(value = "/get_switch_all")
	public Map<String, Object>  getSwitchAll(
			@RequestParam(required = false, value = "weipai_userid") final String user_id) throws ReturnException {

		final String uid = XThreadLocal.getInstance().getCurrentUser();
		if (uid == null) {
			return error("2019");
		}
		
		Map<String, Object> resultMap = new HashMap<String, Object>();
		List<Map<String,Object>> kvList = new ArrayList<Map<String,Object>>();

		resultMap.put("state", "0");
		resultMap.put("switches_status", kvList);
		SettingView settingView = null;	
		try {
			settingView = new LocalCache<SettingView>() {

				@Override
				public SettingView getAliveObject() throws Exception {
					return settingService.getSettingByUid(uid);
				}
				
			}.put(30, "setting"+uid);
		} catch (Exception e) {
			e.printStackTrace();
			return resultMap;
		}
		
		if (settingView == null) {
			return resultMap;
		}
		SettingParam settingParams = new SettingParam();
		BeanUtils.copyProperties(settingView, settingParams);
		List<Map<String,Object>> list = ReflectionUtils.getFiledsInfo(settingParams);
		
		
		for (Map<String, Object> fieldmap : list) {
			Map<String, Object> dataMap = new HashMap<String, Object>();
			dataMap.put("key", fieldmap.get("name"));
			dataMap.put("value", fieldmap.get("value"));
			kvList.add(dataMap);
		}
		resultMap.put("state", "1");
		resultMap.put("switches_status", kvList);
		return resultMap;
	}
	
	@RequestMapping(value = "/forbidden_device")
	public Map forbiddenDevice() {
		
		try {
			boolean a = deviceService.deviceIsForbidden("1");
			System.out.println(a);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		
		return success();
	}
}