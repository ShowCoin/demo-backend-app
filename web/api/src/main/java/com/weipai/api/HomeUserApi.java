package com.weipai.api;

import com.codiform.moo.curry.Translate;
import com.weipai.annotation.LoginRequired;
import com.weipai.application.MediaDTO;
import com.weipai.application.PictureStory;
import com.weipai.application.StoryUtil;
import com.weipai.application.UserAttrOfMediaDTO;
import com.weipai.application.VideoStory;
import com.weipai.application.feed.FeedDTO;
import com.weipai.common.Adapter;
import com.weipai.common.AntiHotlinking;
import com.weipai.common.Constant.HOME_VIDEO_SORT;
import com.weipai.common.Version;
import com.weipai.common.exception.ReturnException;
import com.weipai.common.exception.ServiceException;
import com.weipai.domain.model.live.Live;
import com.weipai.domain.model.live.LiveManager;
import com.weipai.domain.model.live.LiveRepository;
import com.weipai.domain.model.picture.PictureRepository;
import com.weipai.form.PageForm;
import com.weipai.form.VideoPageForm;
import com.weipai.interceptor.XThreadLocal;
import com.weipai.relation.thrift.view.UserVideoView;
import com.weipai.service.PayService;
import com.weipai.service.RelationService;
import com.weipai.stat.thrift.view.VideoStatView;
import com.weipai.user.thrift.view.UserView;
import com.weipai.video.thrift.view.VideoView;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.Transformer;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class HomeUserApi extends BaseApi {

    private static final Logger log = LoggerFactory
            .getLogger(HomeUserApi.class);

    @Autowired
    private VideoStory videoStory;
    @Autowired
    private PayService payService;
    @Autowired
    private LiveManager liveManager;
    @Autowired
    private PictureStory pictureStory;
    @Autowired
    private LiveRepository liveRepository;
    @Autowired
    private RelationService relationService;
    @Autowired
    private PictureRepository pictureRepository;

    private List<Map<String, String>> getFansList(String currentUser, String uid, int cursor, int count) {
        List<UserView> userViewList = null;
        List<String> uids = new ArrayList<String>();
        try {
            userViewList = relationService.findFansListByUid(uid, cursor, count);
        } catch (ServiceException e) {
            log.error(e.getMessage(), e);
        }

        List<Map<String, String>> userList = new ArrayList<Map<String, String>>();
        if (userViewList != null) {
            for (UserView userView : userViewList) {
                if (userView == null || userView.getId() == null) {
                    continue;
                }
                Map<String, String> map = new HashMap<String, String>();
                map.put("user_id", String.valueOf(userView.getId()));
                map.put("avatar", userView.getProfileImg());
                map.put("nickname", userView.getNickname());
                uids.add(userView.getId());
                map.put("follow", "0");
                map.put("gender", userView.getSex4Display());
                userList.add(map);
            }
        }
        List<Boolean> follows = null;
        if (uids.size() > 0) {
            if (currentUser != null) {
                try {
                    follows = relationService.isFollowed(currentUser, uids);
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }

        }
        if (follows != null && follows.size() > 0 && userList.size() > 0) {
            for (int i = 0; i < userList.size(); i++) {
                Map<String, String> dataMap = userList.get(i);
                try {
                    dataMap.put("follow", follows.get(i) ? "1" : "0");
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }

            }
        }

        return userList;
    }

    private List<Map<String, String>> getFollowList(String currentUser, String uid, Integer cursor,
                                                    Integer count) {
        List<String> uids = new ArrayList<String>();

        List<UserView> userViewList = null;
        try {
            userViewList = relationService.findFollowListByUid(uid, cursor,
                    count);
        } catch (ServiceException e) {
            log.error(e.getMessage(), e);
        }

        List<Map<String, String>> userList = new ArrayList<Map<String, String>>();
        if (userViewList != null) {
            for (UserView userView : userViewList) {
                if (userView == null || userView.getId() == null) {
                    continue;
                }
                Map<String, String> map = new HashMap<String, String>();
                map.put("user_id", String.valueOf(userView.getId()));
                map.put("avatar", userView.getProfileImg());
                map.put("nickname", userView.getNickname());
                uids.add(userView.getId());
                map.put("follow", "0");
                map.put("gender", userView.getSex4Display());
                userList.add(map);
            }
        }

        List<Boolean> follows = null;
        if (uids.size() > 0) {
            if (currentUser != null) {
                try {
                    follows = relationService.isFollowed(currentUser, uids);
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }

        }
        if (follows != null && follows.size() > 0 && userList.size() > 0) {
            for (int i = 0; i < userList.size(); i++) {
                Map<String, String> dataMap = userList.get(i);
                try {
                    dataMap.put("follow", follows.get(i) ? "1" : "0");
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }

            }
        }

        return userList;
    }

    /**
     * 当前登陆用户粉丝列表
     *
     * @param count
     * @param cursor
     * @param relative
     * @return
     */
    @RequestMapping("/user/compresseduserfans")
    @ResponseBody
    public Map compressedUserFans(
            @RequestParam(required = true, value = "user_id") String user_id,
            @RequestParam(required = true, value = "count") Integer count,
            @RequestParam(required = false, value = "cursor") Integer cursor,
            @RequestParam(required = true, value = "relative") String relative) throws ReturnException {

        // 当前登陆用户
//		UserView user = getUserView(XThreadLocal.getInstance().getCurrentUser());
//		if (user == null) {
//			return error("2019");
//		}
        if (cursor == null || cursor < 0) {
            cursor = 0;
        }
        if (count == null || count < 0 || count > 20) {
            count = 20;
        }
        String currentUser = XThreadLocal.getInstance().getCurrentUser();
        String target = currentUser;
        if (user_id == null) {
            if (currentUser == null) {
                return error("2019");
            }
        } else {
            target = user_id;

        }

        List<Map<String, String>> userViewList = getFansList(currentUser, target, cursor, count);

        Map<String, Object> map = new HashMap<>();
        String next_cursor = String.valueOf(cursor + count);
        if (count > userViewList.size()) {
            next_cursor = "";
        }
        map.put("next_cursor", next_cursor);
        map.put("fans_user_list", userViewList);

        return success(map);
    }

    /**
     * 当前登陆用户关注列表
     *
     * @param count
     * @param cursor
     * @param relative
     * @return
     */
    @RequestMapping("/user/compresseduserfollows")
    @ResponseBody
    public Map compressedUserFollows(
            @RequestParam(required = true, value = "user_id") String user_id,
            @RequestParam(required = true, value = "count") Integer count,
            @RequestParam(required = false, value = "cursor") Integer cursor,
            @RequestParam(required = true, value = "relative") String relative) throws ReturnException {

        if (cursor == null || cursor < 0) {
            cursor = 0;
        }
        if (count == null || count < 0 || count > 20) {
            count = 20;
        }

        String currentUser = XThreadLocal.getInstance().getCurrentUser();
        String target = currentUser;
        if (user_id == null) {
            if (currentUser == null) {
                return error("2019");
            }
        } else {
            target = user_id;

        }


        List<Map<String, String>> userViewList = getFollowList(currentUser, target, cursor, count);

        Map<String, Object> map = new HashMap<>();
        String next_cursor = String.valueOf(cursor + count);
        if (count > userViewList.size()) {
            next_cursor = "";
        }
        map.put("next_cursor", next_cursor);
        map.put("follow_user_list", userViewList);

        return success(map);
    }

    /**
     * 当前登陆用户喜欢的视频列表
     *
     * @param count
     * @param cursor
     * @param relative
     * @return
     */
    @RequestMapping("/user/compresseduserlikes")
    @ResponseBody
    public Map compressedUserLikes(
            @RequestParam(required = true, value = "user_id") String user_id,
            @RequestParam(required = true, value = "count") Integer count,
            @RequestParam(required = false, value = "cursor") Integer cursor,
            @RequestParam(required = true, value = "relative") String relative) throws ReturnException {

        if (cursor == null || cursor < 0) {
            cursor = 0;
        }
        if (count == null || count < 0 || count > 20) {
            count = 20;
        }

        String currentUser = XThreadLocal.getInstance().getCurrentUser();
        String target = currentUser;
        if (user_id == null) {
            if (currentUser == null) {
                return error("2019");
            }
        } else {
            target = user_id;

        }
        List<Map<String, String>> videoList = getUserLikes(target,
                cursor, count);

        Map map = new HashMap();
        map.put("prev_cursor", "");
        String next_cursor = String.valueOf(cursor + count);
        if (count > videoList.size()) {
            next_cursor = "";
        }
        map.put("next_cursor", next_cursor);
        map.put("like_video_list", videoList);

        return success(map);
    }

    private List<Map<String, String>> getUserLikes(String uid, Integer cursor,
                                                   Integer count) {
        List<VideoView> videoViewList = null;
        try {
            videoViewList = relationService.findLikeListByUid(uid, cursor,
                    count);
        } catch (ServiceException e) {
            log.error(e.getMessage(), e);
        }

        List<Map<String, String>> videoList = new ArrayList<Map<String, String>>();
        if (videoViewList != null) {
            for (VideoView videoView : videoViewList) {
                Map<String, String> map = new HashMap<String, String>();
                map.put("blog_id", String.valueOf(videoView.getVid()));
                map.put("video_screenshot", Adapter.getScreenshot(videoView.getDefaultImg(), videoView.getFilePath()));
                map.put("video_url", "play_url");
                videoList.add(map);
            }
        }
        return videoList;
    }

    /**
     * 当前登陆用户的视频列表
     *
     * @param pageForm
     * @param currentUser
     * @param uid
     * @param sort
     * @return
     * @throws ReturnException
     */
    @RequestMapping("/user/compresseduservideos")
    @ResponseBody
    @LoginRequired(raiseError = false)
    public Map compressedUserVideos(
            @Validated VideoPageForm pageForm,
            @RequestHeader(value = "Weipai-Userid") String currentUser,
            @RequestParam(value = "user_id") String uid,
            @RequestParam(value = "sort") String sort) throws ReturnException {
        // 当前登陆用户
        String target = getTarget(currentUser, uid);
        String sortKey = getSortKey(sort);
        boolean isSelf = StoryUtil.getIsSelf(currentUser, target);

        Map<String, String> equalConditions = new HashMap<>();
        Map<String, String> neConditions = new HashMap<>();
        if (!isSelf) {
            equalConditions.put("posted", "1");
            equalConditions.put("deleted", "0");
        } else {
            equalConditions.put("deleted", "0");
        }

        List<Map<String, String>> userVideoList = new ArrayList<Map<String, String>>();
        String nextCursor = "";
        try {
            List<UserVideoView> userVideoViews = relationService.findVideosByUidWithNE(target,
                    pageForm.getCursor(), pageForm.getCount(), equalConditions, neConditions, sortKey);
            nextCursor = pageForm.getNextCursor(userVideoViews);

            List<String> vids = (List<String>) CollectionUtils.collect(userVideoViews, new Transformer<UserVideoView, String>() {
                @Override
                public String transform(UserVideoView userVideoView) {
                    return userVideoView.getVid();
                }
            });

            List<VideoView> videoList = null;
            if (vids.size() > 0) {
                videoList = videoService.findVideosByIds(vids, null, null);
            }

            if (videoList != null) {
                int i = 0;
                for (VideoView videoView : videoList) {
                    if (videoView.getDeleted() == 1 && !isSelf) {
                        continue;
                    }
                    Map<String, String> vipStatus = payService.findVipStatus(target);
                    Map<String, String> userVideoMap = renderVideoMap(i, videoView, vipStatus);
                    userVideoList.add(userVideoMap);
                    i++;
                }
            }
        } catch (TException | ServiceException e) {
            log.error(e.getMessage(), e);
        }
        Map<String, Object> resultMap = new HashMap<String, Object>();
        resultMap.put("next_cursor", nextCursor);
        resultMap.put("prev_cursor", String.valueOf(pageForm.getCursor()));
        resultMap.put("user_video_list", userVideoList);
        return success(resultMap);
    }

    private Map<String, String> renderVideoMap(int i, VideoView videoView, Map<String, String> vipStatus) throws TException, ServiceException {
        Map<String, String> userVideoMap = new HashMap<>();
        userVideoMap.put("blog_id", videoView.getVid());
        userVideoMap.put("video_screenshot",
                Adapter.getScreenshot(videoView.getDefaultImg(), videoView.getFilePath()));
        userVideoMap.put("color", Adapter.getColor(i));
        userVideoMap.put("deleted", String.valueOf(videoView.getDeleted()));

        String videoUrl;
        String isInReview;

        if (videoView.getDeleted() == 0) {
            videoUrl = AntiHotlinking.signResource(Adapter.getVideoURL(videoView.getFilePath()));
            isInReview = String.valueOf(1 - videoView.getPosted());
        } else {
            videoUrl = "";
            isInReview = "0";
        }
        userVideoMap.put("video_url", videoUrl);
        userVideoMap.put("is_in_review", isInReview);
        userVideoMap.put("user_id", videoView.getUser());
        userVideoMap.putAll(vipStatus);


        return userVideoMap;
    }

    private String getSortKey(String sort) {
        if (sort != null) {
            if (sort.equals(HOME_VIDEO_SORT.TIME.getSort())) {
                return "time";
            } else if (sort.equals(HOME_VIDEO_SORT.HOT.getSort())) {
                return "play_times";
            }
        } else {
            return "time";
        }
        return null;
    }

    private String getTarget(String currentUser, String uid) throws ReturnException {
        String target;
        if (uid == null) {
            if (currentUser == null) {
                throw new ReturnException("2019");
            } else {
                target = currentUser;
            }
        } else {
            target = uid;
        }
        return target;
    }

    /**
     * 个人微拍主页
     *
     * @param uid
     * @return Map
     */
    @RequestMapping(value = "/user/medium", method = RequestMethod.GET)
    @ResponseBody
    @LoginRequired
    public Map getMedium(
            @Validated VideoPageForm pageForm,
            @RequestHeader(value = "Weipai-Userid") String currentUser,
            @RequestParam(value = "user_id") String uid) throws ReturnException {

        List<MediaDTO> medium;

        // 判断要访问个人微拍主页用户是不是当前登录用户
        boolean isSelf = StoryUtil.getIsSelf(currentUser, uid);
        // 根据是否是当前用户，组装查询equal判断条件
        Map<String, String> equalConditions = buildEqualConditions(isSelf);

        try {
            // 根据uid获取用户信息
            UserAttrOfMediaDTO userAttrOfMediaDTO = getUserAttrOfMediaByUid(uid);
            if (userAttrOfMediaDTO == null) return null;

            // 根据条件查询用户视频列表信息，并抽取出视频id列表
            List<UserVideoView> userVideoViews =
                    relationService.findVideosByUidWithNE(uid, pageForm.getCursor(), pageForm.getCount(), equalConditions, null, "time");
            List<String> vids = (List<String>) CollectionUtils.collect(userVideoViews, new Transformer<UserVideoView, String>() {
                @Override
                public String transform(UserVideoView userVideoView) {
                    return userVideoView.getVid();
                }
            });

            // 根据视频id列表，及media中UserAttrOfMediaDTO属性值，查询组装MediaDTO列表并返回
            medium = getMediumByIdsAndAttr(vids, userAttrOfMediaDTO);

            // 若当前用户正在直播且当前页是首页，在媒体列表首位插入直播信息, 并返回。
            if (0 == pageForm.getCursor())
                medium = insertLiveMediaAtFirst(uid, medium, userAttrOfMediaDTO);
        } catch (Exception e) {
            log.error("fail to return UserMedia", e);
            throw new ReturnException(e);
        }

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("next_cursor", pageForm.getNextCursor(medium));
        resultMap.put("prev_cursor", String.valueOf(pageForm.getCursor()));
        resultMap.put("medium", medium);
        return success(resultMap);
    }

    private Map<String, String> buildEqualConditions(boolean isSelf) {
        Map<String, String> equalConditions = new HashMap<>();
        if (!isSelf) {
            // 当要访问个人微拍主页不是当前登录用户主页时，仅能访问未删除且已发布视频
            equalConditions.put("posted", "1");
            equalConditions.put("deleted", "0");
        } else {
            // 当要访问个人微拍主页是当前登录用户主页时，可访问所有未删除视频
            equalConditions.put("deleted", "0");
        }
        return equalConditions;
    }

    private UserAttrOfMediaDTO getUserAttrOfMediaByUid(String uid) throws ServiceException {
        UserView userView = userService.findUserById(uid);

        if (userView == null) {
            return null;
        }

        return Translate.to(UserAttrOfMediaDTO.class).from(userView);
    }

    private List<MediaDTO> getMediumByIdsAndAttr(List<String> vids, UserAttrOfMediaDTO userAttrOfMediaDTO)
            throws ServiceException {
        if (vids == null || vids.size() <= 0) {
            return new ArrayList<>();
        }

        List<VideoView> videoViews = videoService.findVideosByIds(vids);

        List<MediaDTO> medium = Translate.to(MediaDTO.class)
                .withVariable("user", userAttrOfMediaDTO)
                .fromEach(videoViews);

        List<VideoStatView> videoStatViews = statService.findVideoStatByVids(vids);

        for (int i = 0; i < medium.size() && videoStatViews.size() > 0; i++) {
            medium.get(i).setCommented(videoStatViews.get(i).getCommented());
            medium.get(i).setLikeCount(videoStatViews.get(i).getLiked());
            medium.get(i).setPlayCount(videoStatViews.get(i).getViewed());
        }

        return medium;
    }

    private List<MediaDTO> insertLiveMediaAtFirst(String uid, List<MediaDTO> medium, UserAttrOfMediaDTO userAttrOfMediaDTO) {
        Live live = liveRepository.findByUserId(uid);
        if (live != Live.NOT_FOUND) {
            MediaDTO userMediaDTO = new MediaDTO();
            userMediaDTO.setId(live.getId());
            userMediaDTO.setDefaultImg(live.getCover());
            userMediaDTO.setDesc(live.getTitle());
            userMediaDTO.setCreatedAt(live.getStartAt());
            userMediaDTO.setPlayCount(live.getViewNum());
            userMediaDTO.setLikeCount(live.getLikeNum());
            userMediaDTO.setCommented(live.getCommentNum());
            userMediaDTO.setUrl(liveManager.signPullUrl(live.getStreamId()));
            userMediaDTO.setUser(userAttrOfMediaDTO);
            medium.add(0, userMediaDTO);
        }

        return medium;
    }

    /**
     * 个人主页（新）
     *
     * @param pageForm
     * @param currentUser
     * @param userId
     * @return
     * @throws ReturnException
     */
    @RequestMapping(value = "/user/media_list", method = RequestMethod.GET)
    @ResponseBody
    @LoginRequired
    public Map findMediaList(
            @Validated PageForm pageForm,
            @RequestParam(value = "user_id") String userId,
            @RequestHeader(value = "Weipai-Userid") String currentUser,
            @RequestHeader("Client-Version") Version version,
            @RequestHeader("os") String os
    ) throws ReturnException {

        // 判断要访问个人微拍主页用户是不是当前登录用户
        boolean isSelf = StoryUtil.getIsSelf(currentUser, userId);
        // 根据是否是当前用户，组装查询equal判断条件
        Map<String, String> equalConditions = buildEqualConditions(isSelf);

        List<FeedDTO> feedDTOS = new ArrayList<>();
        try {
            List<UserVideoView> userVideoViews =
                    relationService.findVideosByUidWithNE(userId, pageForm.getCursor(), pageForm.getCount(), equalConditions, null, "time");
            if (userVideoViews != null) {
                for (UserVideoView userVideoView : userVideoViews) {
                    FeedDTO feedDTO = FeedDTO.Factory.from(currentUser, userVideoView, userService, videoService, statService, pictureRepository);
                    if (feedDTO != FeedDTO.Factory.dullFeed) {
                        feedDTOS.add(feedDTO);
                    }
                }
            }
            // 若主播正在直播且主播个人主页是首页，在媒体列表首位插入直播信息。
            if (0 == pageForm.getCursor()) {
                FeedDTO feedDTO = FeedDTO.Factory.fromLive(userId, userService, liveRepository,version,os);
                if (feedDTO != FeedDTO.Factory.dullFeed) feedDTOS.add(0, feedDTO);
            }
        } catch (Exception e) {
            log.error("fail to return feeds", e);
            throw new ReturnException(e);
        }

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("next_cursor", pageForm.getNextCursor(feedDTOS));
        resultMap.put("prev_cursor", String.valueOf(pageForm.getCursor()));
        resultMap.put("feeds", feedDTOS);
        return success(resultMap);
    }


    @RequestMapping(value = "/user/media_delete", method = RequestMethod.POST)
    @ResponseBody
    @LoginRequired
    public Map<String, Object> deleteMedia(
            @RequestHeader(value = "Weipai-Userid") String currentUser,
            @RequestParam(value = "media_id") String mediaId,
            @RequestParam(value = "media_type") String mediaType
    ) throws ReturnException {
        switch (mediaType) {
            case "video":
                videoStory.deleteVideo(currentUser, mediaId);
                break;
            case "picture":
                pictureStory.deletePictureBySelf(currentUser, mediaId);
                break;
            default:
                throw new ReturnException("1002");
        }

        return success();
    }

}