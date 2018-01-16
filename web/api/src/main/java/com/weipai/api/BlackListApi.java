package com.weipai.api;

import com.weipai.common.Adapter;
import com.weipai.common.exception.ReturnException;
import com.weipai.common.exception.ServiceException;
import com.weipai.interceptor.XThreadLocal;
import com.weipai.service.PayService;
import com.weipai.stat.thrift.view.UserStatView;
import com.weipai.user.thrift.view.UserView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Controller
public class BlackListApi extends BaseApi {
	private static final Logger log = LoggerFactory.getLogger(BlackListApi.class);

	private PayService payService;
	/**
	 * 
	 * @return
	 * @throws ServiceException
	 */
	@RequestMapping(value = "/blacklist/black")
	@ResponseBody
	public Map<String, Object> black(
			@RequestParam(value = "target_userid", required = true) String target_userid,
			@RequestParam(value = "action", required = true) int action
	) throws ReturnException {

		String currentUser = XThreadLocal.getInstance().getCurrentUser();
		if (currentUser == null) {
			return error("2019");
		}

		if (action == 1) {
			try {
				userService.add2BlackList(currentUser, target_userid);
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
			
		}
		else {
			try {
				userService.removeFromBlackList(currentUser, target_userid);
			} catch (Exception e) {
				log.error(e.getMessage(),e);
			}
			
		}


	    return success();
	}

	/**
	 *
	 * @return
	 * @throws ServiceException
	 */
	@RequestMapping(value = "/blacklist/list")
	@ResponseBody
	public Map<String, Object> list(
			@RequestParam(value = "cursor", required = false) Integer cursor,
			@RequestParam(value = "count", required = false) Integer count
	) throws ReturnException {
		
		final String currentUser = XThreadLocal.getInstance().getCurrentUser();
		if (currentUser == null) {
			return error("2019");
		}
		
		if (cursor==null||cursor < 0) {
			cursor = 0;
		}
		if (count==null || count < 0 || count>20) {
			count = 20;
		}


		List<String> blackUsers = null;
		try {
			blackUsers=userService.findBlackList(currentUser, cursor, count);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		
		List<UserView> blackUserViews =null;
		try {
			if(blackUsers!=null){
				blackUserViews=userService.findUserListByIds(blackUsers);
			}
			
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		List<UserStatView> userStatViews=null;
		try {
			if(blackUsers!=null){
				userStatViews = statService.findUserStatByUids(blackUsers);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		Iterator<UserStatView> userStatViewsIterator = null;
		if(userStatViews!=null){
			userStatViewsIterator=userStatViews.iterator();
		}
		
		Iterator<Boolean> followStateListIterator = null;
		try {
			if (currentUser != null&&blackUsers!=null) {
				followStateListIterator = relationService.isFollowed(currentUser, blackUsers).iterator();
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		


		Map<String, Object> result = new HashMap<>();

		List<Map<String, Object>> userList = new ArrayList<>();
		if(blackUserViews!=null){
			for (UserView blackUserView : blackUserViews) {
				Map<String, Object> map = new HashMap<>();
				map.put("user_id", blackUserView.getId());
				map.put("intro", blackUserView.getStatus());
				map.put("nickname", blackUserView.getNickname());
				map.put("gender", Adapter.getGender(blackUserView.getSex()));
				map.put("avatar_url", Adapter.getAvatar(blackUserView.getProfileImg()));

				if(userStatViewsIterator!=null){
					UserStatView userStatView = userStatViewsIterator.next();
					map.put("video_num", userStatView.videos);
				}else{
					map.put("video_num", 0);
				}
				
				if(followStateListIterator!=null){
					Boolean followState = followStateListIterator.next();
					map.put("video_num", followState ? "1" : "0");
				}else{
					map.put("video_num", "0");
				}
				
				userList.add(map);
			}
		}
		

		String next_cursor = String.valueOf(cursor + count); 
		if (count>userList.size()) {
			next_cursor = "";
		}
		Map<String, String> vipStatus = payService.findVipStatus(currentUser);
		result.putAll(vipStatus);

		result.put("next_cursor", next_cursor);
		result.put("user_list", userList);
		
		return success(result);

	}
}