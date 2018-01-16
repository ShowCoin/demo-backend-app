package com.weipai.api;

import com.weipai.annotation.LoginRequired;
import com.weipai.application.LiveSquareDTO;
import com.weipai.application.LiveStory;
import com.weipai.application.feed.FeedStory;
import com.weipai.common.Constant;
import com.weipai.common.Version;
import com.weipai.common.exception.ReturnException;
import com.weipai.common.exception.ServiceException;
import com.weipai.form.PageForm;
import com.weipai.form.VideoPageForm;
import com.weipai.interceptor.XThreadLocal;
import com.weipai.service.FeedService;
import com.weipai.struc.VideoInfoParam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class FeedApi extends BaseApi{
	private static final Logger log = LoggerFactory.getLogger(FeedApi.class);
	private final FeedService feedService;
	private final LiveStory liveStory;
	private final FeedStory feedStory;

	@Autowired
	public FeedApi(FeedService feedService, LiveStory liveStory, FeedStory feedStory) {
		this.feedService = feedService;
		this.liveStory = liveStory;
		this.feedStory = feedStory;
	}

	@RequestMapping("/feed/list")
	@ResponseBody
	@LoginRequired
	public Map list(
			@Validated PageForm pageForm,
			@RequestHeader("Weipai-Userid") String userId,
			@RequestHeader("Client-Version") Version version,
			@RequestHeader("os") String os
	) throws ReturnException {
		final Map<String, Object> map = feedStory.list(userId, pageForm,version,os);
		return success(map);
	}

	@RequestMapping("/my_follow_user_video_list")
	@ResponseBody
	@LoginRequired
	public Map feedList(
			@Validated VideoPageForm pageForm,
			@RequestHeader("Weipai-Userid") String uid,
			@RequestHeader("Client-Version") Version clientVersion
	) throws ReturnException {
		Map<String, Object> resultMap= new HashMap<>();

		try {
			List list = new ArrayList<>();
			final Constant.USER_AGENT userAgent = XThreadLocal.getInstance().getUserAgent();
			if (
					(
                        (userAgent == Constant.USER_AGENT.IOS && clientVersion.isGreaterThanOrEquals("6.4.0"))
                        || (userAgent == Constant.USER_AGENT.ANDROID && clientVersion.isGreaterThanOrEquals("1.8.0"))
					)
					&& pageForm.getCursor() == 0) {
				if (! "5620e4314bceb0e49e689317".equals(uid)) {
					final List<LiveSquareDTO> feedList = liveStory.getFeedList(uid);
					if (feedList != null)
						list.addAll(feedList);
				}
			}
			List<VideoInfoParam> videos = feedService.findFollowUserVideoList(uid, pageForm);

			if (videos!=null) {
                list.addAll(videos);
				int size=videos.size();
				if( size > 10 && size % 2 > 0 ){
					videos.remove(size-1);
				}
			}

			resultMap.put("video_list", list);
			resultMap.put("next_cursor", pageForm.getNextCursorAsString());
			return success(resultMap);
		} catch (ServiceException e) {
			log.error(e.getMessage(), e);
			return error();
		}
	}

	@RequestMapping("/follow")
	@ResponseBody
	public Map follow(@Validated VideoPageForm pageForm,
			@RequestHeader("Client-Version") Version clientVersion
	) throws ReturnException {

		String uid = XThreadLocal.getInstance().getCurrentUser();

		if (uid != null){
			try {
				Map<String, Object> result = new HashMap<String, Object>();
				List list = new ArrayList();

				List<String>        vids      = feedService.findFollowVids(uid, pageForm.getCursor(), pageForm.getCount());
				final Constant.USER_AGENT userAgent = XThreadLocal.getInstance().getUserAgent();
				if (userAgent == Constant.USER_AGENT.IOS
						&& clientVersion.isGreaterThanOrEquals("6.4.0")
						&& pageForm.getCursor() == 0) {
					final List<LiveSquareDTO> feedList = liveStory.getFeedList(uid);
					if (feedList != null)
                        list.addAll(feedList);
				}
                List videos = videoService.renderVideoList(vids);
				if (videos != null) {
					list.addAll(videos);
				}

				result.put("next_cursor", pageForm.getNextCursor(videos));
				result.put("video_list", list);

				return success(result);
			} catch (ServiceException e) {
				log.error(e.getMessage(), e);
				return error();
			}
		}else{
			return error("2019");
		}

	}
}
