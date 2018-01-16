package com.weipai.api;

import com.weipai.annotation.LoginRequired;
import com.weipai.common.Adapter;
import com.weipai.common.Constant;
import com.weipai.common.Constant.USER_AGENT;
import com.weipai.common.TimeUtil;
import com.weipai.common.exception.ReturnException;
import com.weipai.common.exception.ServiceException;
import com.weipai.gps.thrift.view.GpsView;
import com.weipai.interceptor.XThreadLocal;
import com.weipai.message.thrift.view.BadgesCountView;
import com.weipai.message.thrift.view.MessageView;
import com.weipai.service.DeviceService;
import com.weipai.service.GpsService;
import com.weipai.service.MessageService;
import com.weipai.user.thrift.view.UserView;
import com.weipai.video.thrift.view.VideoView;

import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
@Controller
public class MessageApi extends BaseApi{
	private static final int USER_INFO_UPDATE_PERIOD = 86400;
	private static final Logger log = LoggerFactory.getLogger(MessageApi.class);
	@Autowired
	DeviceService deviceService;
	@Autowired
	GpsService gpsService;
	@Autowired
	MessageService messageService;

	@RequestMapping("/badges")
	@ResponseBody
	@LoginRequired
	public Map badges(
			@RequestHeader("Weipai-Userid") String currentUser,
			@RequestParam(value = "timestamp", required = false) Integer timestamp
	) throws TException {
		if (timestamp == null) {
			timestamp = TimeUtil.getCurrentTimestamp();
		}

		List<BadgesCountView> badgesCountViews = messageService.countBadgesByUid(currentUser, timestamp);

		Map<String,Object> resultMap = new HashMap<>();
		List<Map<String,Object>> list = new ArrayList<>();

		if (badgesCountViews != null) {
			for (BadgesCountView badgesCountView : badgesCountViews) {
				Map<String, Object> map = new HashMap<>();
				map.put("badge_id", badgesCountView.getAction());
				map.put("count", badgesCountView.getAmount());
				list.add(map);
			}
		}
		resultMap.put("badge_list", list);
		resultMap.put("list", list);
		resultMap.put("timestamp", TimeUtil.getCurrentTimestamp());
		return success(resultMap);
	}

	private boolean isNeedUpdate(UserView userView){
		Integer updateTime = userView.getUpdateTime();
		if(updateTime!=null){
			return true;
		}else{
			int time = (int)(System.currentTimeMillis()/1000);
			return time-updateTime.intValue()>USER_INFO_UPDATE_PERIOD;
		}
	}

	private void userInfoGather(UserView userView,String deviceUuid,GpsView gps){
		if(isNeedUpdate(userView)){
			Map<String,String> map = new HashMap<String,String>();
			map.put("device_uuid", deviceUuid);
			try {
				//deviceService.updateDevice(userView.getId(), map);
				userService.updateUserById(userView.getId(), map);
				Map<String,String> params = new HashMap<String,String>();
				params.put("longitude", String.valueOf(gps.getLatitude()));
				params.put("latitude", String.valueOf(gps.getLongitude()));
				params.put("update_time", String.valueOf(System.currentTimeMillis()/1000));
				gpsService.updateGps(userView.getId(), params);
			} catch (ServiceException e) {
				e.printStackTrace();
			}
		}
	}
	/**
	 * 我的消息
	 * @param currentSize
	 * @param count
	 * @param request
	 * @return
	 */
	@RequestMapping("/my_messages")
	@ResponseBody
	public Map myMessage(@RequestParam(required=false,value="cursor")Integer currentSize,@RequestParam(required=false,value="count")Integer count,HttpServletRequest request,HttpServletResponse response) throws ReturnException {
		response.setContentType("text/html; charset=UTF-8");
		String currentUser = XThreadLocal.getInstance().getCurrentUser();
		if (currentUser == null){
			return error("2019");
		}
		if (currentSize == null || currentSize < 0) {
			currentSize = 0;
		}

		if (count == null || count < 0 || count > 20) {
			count = 20;
		}
//		Map<String,String> params = new HashMap<String,String>();
//		params.put("read_status", String.valueOf(Constant.MESSAGE_READ_STATUS.NO.ordinal()));
		Map<String,Object> resultMap = getMessages(currentSize, count, request, null);

		return success(resultMap);
	}
	/**
	 * 我的朋友消息
	 * @param currentSize
	 * @param count
	 * @param request
	 * @return
	 */
	@RequestMapping("/friends_messages")
	@ResponseBody
	public Map friendsMessages(@RequestParam(required=false,value="cursor")Integer currentSize,@RequestParam(required=false,value="count")Integer count,HttpServletRequest request) throws ReturnException {
		String currentUser = XThreadLocal.getInstance().getCurrentUser();
		if (currentUser == null){
			return error("2019");
		}
		if (currentUser == null){
			return error("2019");
		}
		if (currentSize == null || currentSize < 0) {
			currentSize = 0;
		}

		if (count == null || count < 0 || count > 20) {
			count = 20;
		}
		Map<String,String> params = new HashMap<String,String>();
		params.put("read_status", String.valueOf(Constant.MESSAGE_READ_STATUS.NO.ordinal()));
		params.put("msg_type",String.valueOf(Constant.MESSAGE_TYPE.FRIEND.getCode()));
		Map<String,Object> resultMap = getMessages(currentSize, count, request, params);
		return success(resultMap);
	}

	private Map<String,Object> getMessages(Integer currentSize,Integer count,HttpServletRequest request,Map<String,String> params){
		UserView user = getUserView(XThreadLocal.getInstance().getCurrentUser());
		Map<String,Object> resultMap = new HashMap<String,Object>();
		USER_AGENT userAgent = XThreadLocal.getInstance().getUserAgent();
		String source = userAgent.toOSString();
		List<MessageView> list = null;
		try {
			list=messageService.findMessageList(user.getId(), params, "create_time", currentSize, count);
		} catch (NumberFormatException e) {
			log.error(e.getMessage(), e);
			System.out.println(e);
		} catch (ServiceException e) {
			log.error(e.getMessage(),e);
			System.out.println(e);
		}
		List<Map<String,Object>> resultList = new ArrayList<Map<String,Object>>();

		if(list!=null){
			Map<String,String> param = new HashMap<String,String>();
			param.put("read_status", "1");
			for(MessageView messageView:list){
				Map<String,Object> map = new HashMap<String,Object>();
				map.put("message_device", source);
				map.put("message_id", String.valueOf(messageView.getId()));
				map.put("message_time", String.valueOf(messageView.getCreateTime()));
				map.put("message_content", messageView.getMsg());
				map.put("message_type", Constant.MESSAGE_TYPE.values()[(messageView.getMsgType()-1)].getName());
				map.put("user_id", messageView.getFromUid());
				map.put("user_nickname", messageView.getFromNickName());
				UserView userView = getUserView(messageView.getFromUid());
				map.put("user_avatar", userView.getProfileImg());
				if(messageView.getVid()!=null &&!"".equals(messageView.getVid())){
					map.put("message_vid", messageView.getVid());
					try {
						VideoView videoView = videoService.findVideoByVid(messageView.getVid(), null);
						if(videoView!=null){
							map.put("message_video",Adapter.getScreenshot(videoView.getDefaultImg(), videoView.getFilePath()));
						}
					} catch (ServiceException e) {
						log.error(e.getMessage(),e);
					}
				}else{
					map.put("message_vid", "");
				}
				if(map.get("message_video")==null){
					map.put("message_video","");
				}

				resultList.add(map);
				try {
					messageService.updateMessage(user.getId(), messageView.getId(), param);
				} catch (Exception e) {
					log.error(e.getMessage(), e);
				}

			}
		}
//		try {
//			RemoteCache.remove("badges:"+XThreadLocal.getInstance().getCurrentUser());
//		} catch (Exception e) {
//			log.error(e.getMessage(), e);
//		}
		resultMap.put("prev_cursor", currentSize);
		String next_cursor = String.valueOf((currentSize + count));
		if (count > resultList.size()) {
			next_cursor = "";
		}
		resultMap.put("next_cursor", next_cursor);
		resultMap.put("messages_list",resultList);
		return resultMap;
	}
	@RequestMapping("/delete_messages")
	@ResponseBody
	public Map deleteMessage(@RequestParam(required=false,value="uid")String uid,@RequestParam(required=true,value="messageid")String messageid) throws ReturnException {
		String currentUser = XThreadLocal.getInstance().getCurrentUser();
		if (currentUser == null){
			return error("2019");
		}
		if(!StringUtils.isEmpty(messageid)){

			try {
				if("all".equals(messageid)){
					messageService.deleteMessage(currentUser, null);
				}else{
					messageService.deleteMessage(currentUser, messageid);
				}
			} catch (ServiceException e) {
				e.printStackTrace();
			}
		}
		return success();
	}
}
