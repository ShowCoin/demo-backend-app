package com.weipai.api;

import com.google.common.collect.ImmutableMap;

import com.aliyun.openservices.oss.OSSClient;
import com.aliyun.openservices.oss.model.ObjectMetadata;
import com.codiform.moo.curry.Translate;
import com.weipai.WebUtil;
import com.weipai.annotation.LoginRequired;
import com.weipai.application.VideoDTO;
import com.weipai.application.VideoStory;
import com.weipai.application.activity.WeiPaiActivityStory;
import com.weipai.common.Adapter;
import com.weipai.common.AntiHotlinking;
import com.weipai.common.BeanPropertyCopyUtil;
import com.weipai.common.Constant;
import com.weipai.common.Constant.STAT_ACTION;
import com.weipai.common.Constant.USER_AGENT;
import com.weipai.common.GpsUtil;
import com.weipai.common.IPUtil;
import com.weipai.common.JacksonUtil;
import com.weipai.common.Loader;
import com.weipai.common.TimeUtil;
import com.weipai.common.Version;
import com.weipai.common.cache.LocalCache;
import com.weipai.common.client.kafka.Producer;
import com.weipai.common.exception.ReturnException;
import com.weipai.common.exception.ServiceException;
import com.weipai.gps.thrift.view.GpsView;
import com.weipai.index.thrift.view.VideoIndexView;
import com.weipai.infrastructure.transcode.ali.AliTranscodeManager;
import com.weipai.interceptor.XThreadLocal;
import com.weipai.manage.thrift.view.FakeSquareVersionView;
import com.weipai.manage.thrift.view.FakeSquareView;
import com.weipai.manage.thrift.view.SystemConfigView;
import com.weipai.manage.thrift.view.WaterMarkView;
import com.weipai.manage.thrift.view.WhitelistView;
import com.weipai.relation.thrift.view.UserVideoView;
import com.weipai.search.thrift.view.VideoSearchView;
import com.weipai.service.GpsService;
import com.weipai.service.IndexService;
import com.weipai.service.ManageService;
import com.weipai.service.PayService;
import com.weipai.service.RelationService;
import com.weipai.service.SearchService;
import com.weipai.service.VideoPostDone;
import com.weipai.service.VideoService;
import com.weipai.stat.thrift.view.VideoStatView;
import com.weipai.struc.AnchorKafkaUtil;
import com.weipai.struc.HeaderParams;
import com.weipai.struc.VideoInfoParam;
import com.weipai.user.thrift.view.ThirdBindView;
import com.weipai.user.thrift.view.UserView;
import com.weipai.util.CityUtil;
import com.weipai.video.thrift.view.EffectView;
import com.weipai.video.thrift.view.VideoRelationView;
import com.weipai.video.thrift.view.VideoView;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import static com.weipai.common.Constant.STAT_ACTION.VIDEO_UPLOAD;
import static com.weipai.common.Constant.USER_AUTH_FORBID.UPLOAD_VIDEO;
import static com.weipai.common.Event.E_VIDEO_CREATE;
import static com.weipai.common.client.kafka.KafkaProperties.statTopic;

@Controller
public class VideoApi extends BaseApi {

    private static final Logger log = LoggerFactory.getLogger(VideoApi.class);
    private final static Map<String, String> typeMap = ImmutableMap
            .<String, String>builder().put("third_share_sina", "sina")
            .put("third_share_tencent", "tweibo").put("third_share_qq", "qq")
            .put("third_share_ren", "ren")
            .put("third_share_weixin_session", "weixin")
            .put("third_share_weixin_timeline", "weixin")
            .put("third_share_sms", "sms").put("third_share_mail", "mail")
            .put("third_publish_sina", "sina")
            .put("third_publish_tencent", "tweibo")
            .put("third_publish_qq", "qq").put("third_publish_ren", "ren")
            .build();
    private final static Map<String, String> sharedTypeMap = ImmutableMap
            .<String, String>builder().put("sina", "sina").put("ren", "ren")
            .put("qq", "qq").put("wechat", "weixin").build();
    private final static Map<String, String> loginPlatformMap = ImmutableMap
            .<String, String>builder().put("third_login_sina", "sina")
            .put("third_login_ren", "ren").put("third_login_qq", "qq")
            .put("third_login_weixin", "weixin")
            .put("third_login_tencent", "tweibo").build();
    @Autowired
    private ManageService manageService;
    @Autowired
    private VideoService videoService;
    @Autowired
    private GpsService gpsService;
    @Autowired
    private RelationService relationService;
    @Autowired
    private IndexService indexService;
    @Autowired
    private SearchService searchService;
    @Autowired
    private PayService payService;
    @Autowired
    private VideoStory videoStory;
    @Autowired
    private AliTranscodeManager aliTranscodeManager;
    @Autowired
    private WeiPaiActivityStory weiPaiActivityStory;

    /**
     * 返回码率值
     */
    @RequestMapping("/get_record_params")
    @ResponseBody
    public Map getRecordParams() {
        Map<String, Object> map = new HashMap<String, Object>();
        // 查询系统配置表 参数
        try {
            SystemConfigView systemConfigView = new LocalCache<SystemConfigView>() {
                @Override
                public SystemConfigView getAliveObject() throws Exception {
                    return manageService
                            .getSystemConfig(Constant.SYSTEM_CONFIG_ID);
                }
            }.put(60 * 30, "systemConfig");

            map.put("videoRate", systemConfigView.getVideoFrame());
        } catch (Exception e) {
            map.put("videoRate", "1024000");
            log.error("-----getSystemConfig error!!-----" + e.getMessage(), e);
        }

        return success(map);
    }

    /**
     * 获取文案模板
     */
    @RequestMapping("/get_template")
    @ResponseBody
    public Map getTemplate(
            @RequestParam(required = true, value = "template_type") String templateType,
            @RequestParam(required = false, value = "blog_id") String blogId) throws ReturnException {
        SystemConfigView systemConfigView = null;
        try {
            systemConfigView = manageService.getSystemConfig(String.valueOf(1));
        } catch (ServiceException e) {
            log.error(e.getMessage(), e);
        }
        Map<String, String> resultMap = new HashMap<String, String>();
        String[] videoArr = new String[]{"third_publish_sina",
                "third_publish_tencent", "third_publish_qq",
                "third_publish_ren"};
        List<String> videoTypeList = Arrays.asList(videoArr);
        String url = "";
        String uid = XThreadLocal.getInstance().getCurrentUser();
        if (uid == null) {
            return error("2015");
        }

        if (videoTypeList.contains(templateType)) {
            url = "http://www.weipai.cn/video/uuid/" + blogId + "?type="
                    + templateType;
            if (systemConfigView != null) {
                if (templateType.equals("third_publish_sina")) {
                    resultMap.put("tpl_content", systemConfigView.getShareSina());
                } else if (templateType.equals("third_publish_qq")) {
                    resultMap.put("tpl_content", systemConfigView.getShareQq());
                } else if (templateType.equals("third_publish_tencent") || templateType.equals("third_share_qzone")) {
                    resultMap.put("tpl_content", systemConfigView.getShareQqzone());
                }
            }
        } else if (loginPlatformMap.containsKey(templateType)) {
            url = "http://www.weipai.cn/download";
            resultMap.put("login_share_img_url", "http://aliv.weipai.cn/images/login_share_img_url.jpg?v=1");
            if (systemConfigView != null) {
                if (templateType.equals("third_login_sina")) {
                    resultMap.put("tpl_content", systemConfigView.getShareSina());
                } else if (templateType.equals("third_login_qq")) {
                    resultMap.put("tpl_content", systemConfigView.getShareQq());
                } else if (templateType.equals("third_login_tencent") || templateType.equals("third_share_qzone")) {
                    resultMap.put("tpl_content", systemConfigView.getShareQqzone());
                } else if (templateType.equals("third_login_weixin")) {
                    resultMap.put("tpl_content", systemConfigView.getShareWx());
                }
            }
        } else {
            VideoView videoView = null;
            if (isVideoId(blogId)) {
                url = "http://www.weipai.cn/video/" + blogId + "?type="
                        + templateType;
            } else {
                try {
                    url = "http://www.weipai.cn/video/uuid/" + blogId
                            + "?type=" + templateType;
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
        String[] strArr = new String[]{"third_gift", "third_comment",
                "third_enjoy"};
        List<String> strList = Arrays.asList(strArr);
        if (typeMap.containsKey(templateType)) {
            url = url + "&platform=" + typeMap.get(templateType);
            if (systemConfigView != null) {
                if (templateType.equals("third_share_sina")) {
                    resultMap.put("tpl_content", systemConfigView.getShareSina());
                } else if (templateType.equals("third_share_qq")) {
                    resultMap.put("tpl_content", systemConfigView.getShareQq());
                } else if (templateType.equals("third_share_weixin_session")) {
                    resultMap.put("tpl_content", systemConfigView.getShareWx());
                } else if (templateType.equals("third_share_weixin_timeline")) {
                    resultMap.put("tpl_content", systemConfigView.getShareWxtimeline());
                } else if (templateType.equals("third_publish_tencent") || templateType.equals("third_share_tencent") || templateType.equals("third_share_qzone")) {
                    resultMap.put("tpl_content", systemConfigView.getShareQqzone());
                }
            }
        } else if (strList.contains(templateType)) {
            try {
                List<ThirdBindView> thirdBindViewList = userService
                        .findThirdBindByUid(uid);
                if (thirdBindViewList != null && thirdBindViewList.size() > 0) {
                    String shardType = sharedTypeMap.get(thirdBindViewList.get(
                            0).getType());

                    if (shardType != null && !shardType.equals("")) {
                        url = url + "&platform=" + shardType;
                    }
                }
            } catch (ServiceException e) {
                e.printStackTrace();
            }
        }
        // TODO后台可配置项
        if (resultMap.get("tpl_content") == null) {
            if (templateType.equals("third_share_tencent") || templateType.equals("third_publish_tencent") || templateType.equals("third_share_weixin_session") || templateType.equals("third_share_weixin_timeline") || templateType.equals("third_share_qq") || templateType.equals("third_share_qzone")) {
                resultMap.put("tpl_content", "爱微拍 要想红，玩微拍");
            } else {
                resultMap.put("tpl_content", "@爱微拍 要想红，玩微拍");
            }

        }

        resultMap.put("tpl_videourl", url);

        return success(resultMap);
    }

    /**
     * @param platform
     * @param version
     * @param userid
     * @return
     */
    @RequestMapping("/Topmessage")
    @ResponseBody
    public Map Topmessage(
            @RequestParam(required = false, value = "platform") String platform,
            @RequestParam(required = false, value = "version") String version,
            @RequestParam(required = false, value = "user_id") String userid) {
        // 发现传过来的参数然并卵
        Map<String, Object> resultMap = new HashMap<String, Object>();
        resultMap.put("message_id", "m0001");
        resultMap.put("type", 1);
        resultMap.put("image", "");
        resultMap.put("content", "");
        resultMap.put("title", "");
        Map<String, Map<String, String>> mapParams = new HashMap<String, Map<String, String>>();
        String[] arr = new String[]{"确认", "取消", "查看明细"};
        String[] strArr = new String[]{"ok", "cancel", "detail"};
        for (int i = 0; i < 3; i++) {
            Map<String, String> mapParam = new HashMap<String, String>();
            mapParam.put("title", arr[i]);
            mapParam.put("callback", "");
            mapParams.put(strArr[i], mapParam);

        }
        resultMap.put("buttontitles", mapParams);

        return success(resultMap);

    }

    @RequestMapping(value = "/user_video_list")
    @ResponseBody
    @LoginRequired
    public Map userVideoList(@RequestParam(value = "blog_id") String vid
            , @RequestHeader("Client-Version") Version clientVersion
            , @RequestHeader("Channel") String channel
    ) throws ReturnException {

        String currentUser = XThreadLocal.getInstance().getCurrentUser();
        USER_AGENT userAgent = XThreadLocal.getInstance().getUserAgent();

        UserView user = getUserView(currentUser);
        if (user == null) {
            throw new ReturnException("5005");
        }
        Map<String, Object> resultMap = new HashMap<>();
        try {
            resultMap.put("next_cursor", "");
            resultMap.put("prev_cursor", "");

            List<VideoInfoParam> videoList = new ArrayList<>();
            VideoInfoParam videoInfo = videoService.findVideoPageInfo(vid, user.getId(), clientVersion);
            if (videoInfo == null) {
                return error("7001");
            }

			boolean isVideoVip = false;
			boolean isDMVip = false;

			if (channel.equals("dianxin")) {
				long dmExpirationTime = payService.getDMVipExpirationTime(currentUser);
				if (System.currentTimeMillis() > dmExpirationTime) {
					throw new ReturnException("5016");
				}else {
					isDMVip = true;
				}
			}String vipExpire =  payService.findUserPayInfo(user.getId()).getVipExpire();
			if(vipExpire != null && !vipExpire.equals("0") && !vipExpire.equals("")){
				if(TimeUtil.getCurrentTimestamp() < Integer.parseInt(vipExpire)){
					isVideoVip = true;
				}
			}

            int watchFee = -1;
            if (!isDMVip && !isVideoVip && !user.getId().equals(videoInfo.getUser_id())) {
                watchFee = payService.playVideo(user.getId(), videoInfo.getUser_id(), vid);
                if (watchFee < 0) {
                    return error("8012");
                }
            }

            if (!videoInfo.getUser_id().equals(user.getId())) {
                if (videoInfo.getPosted() != 1) {
                    return error("7003");
                }
            }

            if (manageService.isFake(userAgent, clientVersion.toString(), channel)) {
                List<FakeSquareView> fakeSquareViews = manageService.findFakeSquare(0, 20, "asc");
                for (FakeSquareView fakeSquareView : fakeSquareViews) {
                    if (fakeSquareView.getVid().equals(videoInfo.getVideo_id())) {
                        videoInfo.setVideo_play_url(fakeSquareView.getVideoUrl());
                        videoInfo.setVideo_url(fakeSquareView.getVideoUrl());
                        videoInfo.setVideo_screenshots(fakeSquareView.getVideoImg());
                        videoInfo.setVideo_screenshots_v(fakeSquareView.getVideoImg());
                    }
                }
            }

            videoList.add(videoInfo);
            WebUtil.filterVideoUrls(userAgent, currentUser, videoList);

            resultMap.put("video_list", videoList);


            produceKafkaMsg(vid, userAgent, videoInfo, watchFee);
            if (watchFee > 0) {
                AnchorKafkaUtil.updateAnchorBalance(videoInfo.getUser_id(), watchFee / 2, 0);
            }
        } catch (ReturnException e) {
            throw e;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return error("0");
        }

        return success(resultMap);
    }

    private void produceKafkaMsg(String vid, USER_AGENT userAgent, VideoInfoParam videoInfo, int watchFee) {
        try {
            HeaderParams headerParams = XThreadLocal.getInstance().getHeaderParams();
            String version = headerParams.getClientVersion();

            boolean flag = false;
            if (version != null) {

                if (userAgent == USER_AGENT.IOS) {
                    if (version.compareTo("5.0.0") >= 0) {
                        flag = true;
                    }
                }
                if (userAgent == USER_AGENT.ANDROID) {
                    if (version.compareTo("1.0.0.0") >= 0) {
                        flag = true;
                    }
                }
            }

            if (flag) {
                Map map = new HashMap();
                map.put("vid", vid);
                map.put("tid", videoInfo.getUser_id());
                map.put("action", STAT_ACTION.PLAY.toString());
                map.put("time", (int) (System.currentTimeMillis() / 1000));
                map.put("watch_fee", watchFee);
                Producer.getInstance().sendData(statTopic,
                        JacksonUtil.writeToJsonString(map));
            }
        } catch (Exception e) {
            // TODO: handle exception
        }
    }

    @RequestMapping(value = "/play_video")
    @ResponseBody
    public Map playVideo(
            @RequestParam(value = "blog_id") String vid,
            @RequestParam(value = "user_id", required = false) String userId) throws ReturnException {

        // 取得当前登录用户
        String uid = XThreadLocal.getInstance().getCurrentUser();

        Map<String, Object> resultMap = new HashMap<>();
        VideoView videoView;
        try {
            Map<String, String> paramMap = new HashMap<>();
            // paramMap.put("posted", "1");
            paramMap.put("deleted", "0");
            videoView = videoService.findVideoByVid(vid, paramMap);

            if (videoView == null) {
                return error("7001");
            }

            if (uid == null || !videoView.getUser().equals(uid)) {
                if (videoView.getPosted() != 1) {
                    return error("7003");
                }
            }

            if (!videoView.getUser().equals(uid) && videoView.getCreatedAt() < Constant.CUT_POINT_TIMESTAMP) {
                return error("7004");
            }

            int watchFee = payService.playVideo(uid, videoView.getUser(), vid);
            if (watchFee < 0) {
                return error("8012");
            }

            resultMap.put(
                    "video_url",
                    AntiHotlinking.signResource(Adapter.getVideoURL(videoView.getFilePath())));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return error("0");
        }

        int playNum = 0;
        try {
            VideoStatView videoStatView = statService.findVideoStatByVid(vid);
            if (videoStatView != null) {
                playNum = videoStatView.getViewed() + 1;
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

        // TODO(hehao 16/5):根据现在的统计，我们在/play_video和/user_video_list
        // 请求里都会发送PLAY统计，这样就存在重复计算的问题。由于目前的安卓版本，并
        // 没有在播放时发送/play_video请求，所以修改如下：
        // 只用/user_video_list的次数来统计播放次数，这里会存在进入播放页面，但是没有
        // 自动播放时多统计了的问题。但是，需要等安卓端播放了视频时，调用/play_video
        // 这个修复发版并覆盖绝大部分安装时，才能使用更准确的/play_video来统计。
//		try {
//			Map map = new HashMap();
//			map.put("vid", vid);
//			map.put("tid", videoView.getUser());
//			map.put("action", STAT_ACTION.PLAY.toString());
//			map.put("time", (int) (System.currentTimeMillis() / 1000));
//			Producer.getInstance().sendData(KafkaProperties.statTopic,
//					JacksonUtil.writeToJsonString(map));
//		} catch (Exception e) {
//			log.error(
//					" --------- kafka message error ---------" + e.getMessage(),
//					e);
//		}

        resultMap.put("play_num", playNum);

        return success(resultMap);
    }

    @RequestMapping(value = "/shareThird_Success")
    @ResponseBody
    public Map shareThirdSuccess(
            @RequestParam("blog_id") String videoId,
            @RequestParam(value = "url", required = false) String url,
            @RequestParam(value = "shareType", required = false) String platform,
            @RequestHeader(value = "Weipai-Userid", required = false) String currentUser) {

        Map map = new HashMap();
        map.put("vid", videoId);
        map.put("action", STAT_ACTION.SHARE.toString());
        map.put("share_type", platform);  // 分享平台
        map.put("time", TimeUtil.getCurrentTimestamp());
        Producer.getInstance().sendData(statTopic,
                JacksonUtil.writeToJsonString(map));

        String key = "weipai:api:shareThird_Success:currentUser-" + currentUser + ":url-" + url + ":platform-" + platform;

        return success();
    }

    @RequestMapping(value = "/video/publishshare")
    @ResponseBody
    public Map publishShare(
            @RequestParam(required = true, value = "contents") String contents,
            @RequestParam(required = true, value = "device") String device,
            @RequestParam(required = true, value = "platform") String platform,
            @RequestParam(required = true, value = "uid") String uid,
            @RequestParam(required = true, value = "uuid") String uuid) throws ReturnException {

        if (StringUtils.isEmpty(contents) || StringUtils.isEmpty(device)
                || StringUtils.isEmpty(platform) || StringUtils.isEmpty(uid)
                || StringUtils.isEmpty(uuid)) {
            return error("1002");
        }

        log.info("publishshare success! contents :" + contents + ",device:"
                + device + ",platform:" + platform + ",uid:" + uid + ",uuid"
                + uuid);

        return success();
    }

    @RequestMapping(value = "/video/antiporn")
    @ResponseBody
    public Map antiPorn(
            @RequestParam(required = true, value = "blog_id") String uuid,
            @RequestParam(required = true, value = "label") int label,
            @RequestParam(required = true, value = "review") int review) throws ReturnException {
        if (StringUtils.isEmpty(uuid)) {
            return error("1002");
        }

        try {
            VideoRelationView videoRelationView = videoService.findVideoRelationByUUid(uuid);
            String vid = videoRelationView.vid;
            VideoView videoView = videoService.findVideoByVid(vid, null);
            if (videoView.getDeleted() == 1) {
                return error("7001");
            }
            Map<String, String> params = new HashMap<String, String>();
            if (review == 0) {
                switch (label) {
                    case 0://it is porn
                        params.put("porn_tp", "1");
                        break;
                    case 1://it is sexy
                        params.put("porn_tp", "3");
                        break;
                    case 2://it is normal
                        params.put("porn_tp", "2");
                        break;
                    default:
                        break;
                }
            } else {
                params.put("porn_tp", "3");
            }
//			params.put("normal_tp", "1");
            indexService.updateVideoIndex(vid, params);
        } catch (ServiceException e) {
            return error();
        }
        return success();
    }

    /**
     * 创建视频（新版使用）
     *
     * @param currentUser
     * @param path
     * @param request
     * @return
     * @throws ReturnException
     */
    @RequestMapping(value = "/video/post")
    @ResponseBody
    @LoginRequired
    public Map videoPost(
            @RequestParam(value = "movie_url") String path,
            @RequestHeader("Weipai-Userid") String currentUser,
            HttpServletRequest request) throws ReturnException {

        if (isForbidden(currentUser, UPLOAD_VIDEO.getIndex())) {
            return error("5002");
        }

        VideoView videoView = new VideoView();
        fillVideoView(currentUser, videoView, request);
        String uuid = videoView.getUuid();
        try {
            VideoRelationView videoRelationView = videoService.findVideoRelationByUUid(uuid);
            String vid;
            if (videoRelationView != null) {
                vid = videoRelationView.getVid();
                updateVideo(vid, request);
                log.info("update video : {}", vid);
            } else {
                vid = createVideo(request, videoView);
                log.info("create video : {}", vid);
            }
            videoView.setVid(vid);
        } catch (Exception e) {
            log.error("saveVideo error", e);
            return error("0");
        }

        aliTranscodeManager.submitTranscodeJob(path);
        Map<String, Object> map = new HashMap<>();
        if (StringUtils.isEmpty(videoView.getDesc())) {
            videoView.setDesc("我上传了新视频，赶快来看看吧");
        }
        //发布kafka消息
        Map<String, Object> data = new HashMap<>();
        data.put("uid", videoView.getUser());
        data.put("vid", videoView.getVid());
        data.put("time", (int) (System.currentTimeMillis() / 1000));
        Producer.getInstance().sendData(E_VIDEO_CREATE.name(), JacksonUtil.writeToJsonString(data));

        map.put("video", Translate.to(VideoDTO.class).from(videoView));
        return success(map);
    }

    @RequestMapping(value = "/video/save")
    @ResponseBody
    public Map videoSave(
            @RequestParam(value = "user_id") final String userId,
            HttpServletRequest request) throws ReturnException {

        if (StringUtils.isEmpty(userId)) {
            return error("1002");
        }
        if (isForbidden(userId, UPLOAD_VIDEO.getIndex())) {
            return error("5002");
        }

        VideoView videoView = new VideoView();
        fillVideoView(userId, videoView, request);
        String uuid = videoView.getUuid();
        try {
            VideoRelationView videoRelationView = videoService.findVideoRelationByUUid(uuid);
            if (videoRelationView != null) {
                String vid = videoRelationView.getVid();
                updateVideo(vid, request);
                log.info("update video : {}", vid);
            } else {
                String vid = createVideo(request, videoView);
                log.info("create video : {}", vid);
            }
        } catch (Exception e) {
            log.error("saveVideo error", e);
            return error("0");
        }
        return success();
    }

    private String createVideo(HttpServletRequest request, VideoView videoView) throws ServiceException {
        judgeVideoPost(videoView);
        String vid = videoService.saveVideo(videoView);
        videoView.setVid(vid);
        saveEffectView(videoView, request);
        saveUserVideo(videoView);
        saveVideoRelation(videoView);
        saveVideoIndex(videoView, request);
        postMsg(videoView);
        return vid;
    }

    private void postMsg(VideoView videoView) {
        try {
            // 视频发布之后后续操作
            if (videoView.getPosted() == 1) {
                VideoPostDone videoPostDone = VideoPostDone
                        .getInstance();
                videoPostDone.afterPost(videoView.getUser(), videoView.getVid());
            }

            //发布kafka消息
            Map<String, Object> map = new HashMap<>();
            map.put("uid", videoView.getUser());
            map.put("vid", videoView.getVid());
            map.put("action", VIDEO_UPLOAD.toString());
            map.put("time", (int) (System.currentTimeMillis() / 1000));

            Producer.getInstance().sendData(statTopic, JacksonUtil.writeToJsonString(map));
            Producer.getInstance().sendData(E_VIDEO_CREATE.name(), JacksonUtil.writeToJsonString(map));
        } catch (Exception e) {
            log.error("afer post do error!", e);
        }
    }

    /**
     * 同城视频列表
     *
     * @param request
     * @param
     * @param count
     * @return
     */
    @RequestMapping("/same_city_videos")
    @ResponseBody
    public Map sameCityVideos(HttpServletRequest request,
                              @RequestParam(value = "cursor", required = false) Integer cursor,
                              @RequestParam(value = "count", required = true) Integer count, @RequestParam(value = "test", required = false) String test) throws ReturnException {
        final String uid = XThreadLocal.getInstance().getCurrentUser();
        if (uid == null) {
            return error("2019");
        }


        final UserView user = getUserView(uid);
        if (cursor == null || cursor < 0) {
            cursor = 0;
        }

        if (count == null || count < 0 || count > 20) {
            count = 20;
        }
        Map<String, Object> responseMap = new HashMap<String, Object>();
        String longitude = request.getHeader("Longitude");
        String latitude = request.getHeader("Latitude");
        if (longitude != null && latitude != null) {
            if (longitude.indexOf("+") == 0) {
                longitude = longitude.replaceAll("\\+", "");
            }
            if (latitude.indexOf("+") == 0) {
                latitude = latitude.replaceAll("\\+", "");
            }
        }

        final String lng = longitude;

        final String lat = latitude;
        String version = request.getHeader("Client-Version");
        String ip = IPUtil.getIP(request);
        if (cursor == 0) {
            new Thread() {
                @Override
                public void run() {
                    if (lng != null && !"".equals(lng) && Double.valueOf(lng) > 0 && lat != null
                            && !"".equals(lat) && Double.valueOf(lat) > 0) {

                        try {
                            Map<String, String> params = new HashMap<String, String>();
                            params.put("lng", lng);
                            params.put("lat", lat);
                            userService.updateUserById(uid, params);
                        } catch (ServiceException e) {
                            log.error(e.getMessage(), e);
                        }
                        try {
                            GpsView gpsView = gpsService.findGpsByUid(uid);
                            if (gpsView != null) {
                                Map<String, String> params = new HashMap<String, String>();
                                params.put("latitude", lat);
                                params.put("longitude", lng);
                                if (user.getSex4Display() != null) {
                                    params.put("gender", user.getSex4Display());
                                }

                                params.put("city", CityUtil.getCityName(Double.valueOf(lat), Double.valueOf(lng)));
                                params.put("update_time", String.valueOf((int) (System.currentTimeMillis() / 1000)));
                                gpsService.updateGps(uid, params);
                            } else {
                                gpsView = new GpsView();
                                double lati = Double.valueOf(lat).doubleValue();
                                double lngi = Double.valueOf(lng).doubleValue();
                                gpsView.setCity(CityUtil.getCityName(Double.valueOf(lat), Double.valueOf(lng)));
                                gpsView.setLatitude(lati);
                                gpsView.setLongitude(lngi);
                                gpsView.setUpdateTime((int) (System.currentTimeMillis() / 1000));
                                gpsView.setUid(uid);
                                gpsView.setGender(user.getSex4Display());
                                gpsService.saveGps(gpsView);
                            }

                        } catch (Exception e) {
                            log.error(e.getMessage(), e);
                        }
                    }
                }
            }.start();
        }
        boolean fakeStatus = true;
        try {
            List<FakeSquareVersionView> list = null;
            List<String> versions = new ArrayList<String>();
            USER_AGENT userAgent = XThreadLocal.getInstance().getUserAgent();
            String os = userAgent.toOSString();
            if (os.equals("Android")) {
                list = manageService.findFakeSquareVersionList(os);
                if (list != null) {
                    for (FakeSquareVersionView fakeSquareVersionView : list) {
                        versions.add(fakeSquareVersionView.getAndroidVersion());
                    }
                }

            } else {
                list = manageService.findFakeSquareVersionList(os);
                for (FakeSquareVersionView fakeSquareVersionView : list) {
                    versions.add(fakeSquareVersionView.getIosVersion());
                }
            }
            if (versions.contains(version)) {
                fakeStatus = true;
            }
        } catch (ServiceException e1) {
            log.error(e1.getMessage(), e1);
        }
        if (test != null && !"".equals(test)) {
            fakeStatus = false;
        }
        if (fakeStatus) {
            List<Map<String, String>> resultList = new ArrayList<Map<String, String>>();
            try {
                List<FakeSquareView> fakeSquareViews = manageService.findFakeSquare(0, 20, "desc");
                if (fakeSquareViews != null) {
                    int i = 0;
                    for (FakeSquareView fakeSquareView : fakeSquareViews) {
                        Map<String, String> map = new HashMap<String, String>();
                        map.put("video_id", fakeSquareView.getVid());
                        map.put("video_url", AntiHotlinking.signResource(Adapter.getVideoURL(fakeSquareView.getVideoUrl())));
                        map.put("videoImage", Adapter.getScreenshot(
                                fakeSquareView.getVideoImg(), fakeSquareView.getVideoUrl()));
                        map.put("distance", fakeSquareView.getDistance());
                        map.put("color", Adapter.getColor(i));
                        resultList.add(map);
                        i++;
                    }

                }
                responseMap.put("nearByVideoList", resultList);
                responseMap.put("prev_cursor", "0");
                responseMap.put("next_cursor", "");
            } catch (ServiceException e) {
                log.error(e.getMessage(), e);
            }
        } else {
            String city = "";
            List<String> vids = null;
            if (lng != null && !"".equals(lng) && Double.valueOf(lng) > 0 && lat != null
                    && !"".equals(lat) && Double.valueOf(lat) > 0) {
                try {
                    Map<String, Object> map = new LocalCache<Map<String, Object>>() {

                        @Override
                        public Map<String, Object> getAliveObject()
                                throws Exception {
                            return GpsUtil.getUserGps(Double.parseDouble(lat), Double.parseDouble(lng));
                        }
                    }.put(10 * 60, "user_city_" + uid);
                    if (map != null) {
                        Map<String, Object> map1 = (Map<String, Object>) map
                                .get("result");
                        if (map1 != null) {
                            Map<String, Object> address = (Map<String, Object>) map1
                                    .get("addressComponent");
                            if (address != null) {
                                city = (String) address.get("city");
                            }
                        }
                    }
                    Map<String, String> paramMap = new HashMap<String, String>();
                    paramMap.put("deleted", "0");
                    int province = 40;
                    if (city.contains("北京")) {
                        paramMap.put("province", String.valueOf(40));
                    } else {
                        Integer cityNum = CityUtil.getCityNumDetail(Double.parseDouble(lng), Double.parseDouble(lat), ip).get("city");
                        if (cityNum != null) {
                            paramMap.put("province", String.valueOf(cityNum / 100));
                            province = cityNum / 100;
                        } else {
                            paramMap.put("province", String.valueOf(0));
                            province = 0;
                        }

                    }
                    vids = indexService.findSameCityVids(cursor, count, province);

                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }

            } else {
                if (ip == null || "".equals(ip)) {
                    log.info("走到这儿的时候实在是不知道该如何拿同城列表了，只能报个错误了");
                    return error("1002");
                }
                int province = CityUtil.getProvice(ip);
                try {
                    Map<String, String> paramMap = new HashMap<String, String>();
                    paramMap.put("province", String.valueOf(province));
                    vids = indexService.findSameCityVids(cursor, count, province);
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }

            }

            List<Map<String, Object>> resultList = new ArrayList<Map<String, Object>>();
            int i = 0;
            if (vids != null) {
                for (String vid : vids) {
                    Map<String, Object> result = new HashMap<String, Object>();
                    Map<String, String> paramMap = new HashMap<String, String>();
                    paramMap.put("posted", "1");
                    paramMap.put("deleted", "0");
                    VideoView videoView = null;
                    try {
                        videoView = videoService.findVideoByVid(vid, paramMap);
                    } catch (ServiceException e) {
                        log.error(e.getMessage(), e);
                    }
                    if (videoView != null) {
                        result.put("video_id", videoView.getVid());
                        try {
                            String videoUrl = AntiHotlinking.signResource(Adapter.getVideoURL(videoView.getFilePath()));
                            if (videoUrl != null) {
                                result.put("video_url", videoUrl);
                            } else {
                                result.put("video_url", "");
                            }
                        } catch (ServiceException e) {
                            log.error(e.getMessage(), e);
                        }
                        result.put("videoImage", Adapter.getScreenshot(
                                videoView.getDefaultImg(), videoView.getFilePath()));
                        result.put("color", Adapter.getColor(i));
                        result.put(
                                "distance",
                                (int) Math.floor(GpsUtil.distance(
                                        Double.parseDouble(lng),
                                        Double.parseDouble(lat),
                                        videoView.getLongitude(),
                                        videoView.getLatitude())));
                        resultList.add(result);
                        i++;
                    }

                }
            }
            int size = resultList.size();
            if (size > 10 && size % 2 > 0) {
                resultList.remove(size - 1);
            }
            responseMap.put("prev_cursor", cursor);
            String next_cursor = String.valueOf((cursor + count));
            if (resultList == null || resultList.size() == 0) {
                next_cursor = "";
            }
            responseMap.put("next_cursor", next_cursor);
            responseMap.put("nearByVideoList", resultList);
        }

        return success(responseMap);
    }

    /**
     * 水印列表
     *
     * @return
     */
    @RequestMapping("/water_mark_list")
    @ResponseBody
    public Map waterMarkList() {
        final String uid = XThreadLocal.getInstance().getCurrentUser();
        Map<String, Object> resultMap = new HashMap<String, Object>();
        try {
            List<Map<String, String>> list = new LocalCache<List<Map<String, String>>>() {

                @Override
                public List<Map<String, String>> getAliveObject() throws Exception {
                    List<WaterMarkView> marks = manageService.findWaterMark();
                    List<Map<String, String>> list = new ArrayList<Map<String, String>>();
                    if (marks != null) {
                        for (WaterMarkView waterMarkView : marks) {
                            Map<String, String> map = new HashMap<String, String>();
                            map.put("id", String.valueOf(waterMarkView.getId()));
                            map.put("editStatus", String.valueOf(waterMarkView.getEditStatus()));
                            map.put("img", waterMarkView.getImg());
                            map.put("littleImg", waterMarkView.getLittleImg());
                            map.put("sort", String.valueOf(waterMarkView.getSort()));
                            list.add(map);
                        }
                    }
                    return list;
                }
            }.put(60, "video_mark_" + uid);
            resultMap.put("warterMarkList", list);
        } catch (ServiceException e) {
            log.error(e.getMessage(), e);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return success(resultMap);
    }

    /**
     * 填充videoview信息
     *
     * @param uid
     * @param videoView
     * @param request
     */
    private void fillVideoView(String uid, VideoView videoView,
                               HttpServletRequest request) {
        initVideoView(uid, videoView);

        extractVideoView(videoView, request);
    }

    private void extractVideoView(VideoView videoView, HttpServletRequest request) {
        Map<String, Object> map = extractVideoMap(request);
        try {
            BeanPropertyCopyUtil.copyPropertiesFromMap(map, videoView, map.keySet());
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> extractVideoMap(HttpServletRequest request) {
        Map<String, Object> map = new HashMap<>();
        map.put("client_version", getStringParameter(request, "c_ver"));
        map.put("client_agent", getStringParameter(request, "client_agent"));
        map.put("desc", getStringParameter(request, "blog_content"));
        map.put("effect", getStringParameter(request, "effect"));
        map.put("width", getIntParameter(request, "width"));
        map.put("height", getIntParameter(request, "height"));
        map.put("length", getDoubleParameter(request, "len"));
        map.put("m3u8", getIntParameter(request, "m3u8"));
        map.put("upload_start_time", getIntParameter(request, "upload_start_time"));
        map.put("upload_stop_time", getIntParameter(request, "upload_stop_time"));
        map.put("process_start_time", getIntParameter(request, "process_start_time"));
        map.put("process_stop_time", getIntParameter(request, "process_stop_time"));
        map.put("publish_start_time", getIntParameter(request, "publish_start_time"));
        map.put("publish_stop_time", getIntParameter(request, "publish_stop_time"));
        map.put("record_start_time", getIntParameter(request, "record_start_time"));
        map.put("record_stop_time", getIntParameter(request, "record_stop_time"));
        map.put("uuid", getStringParameter(request, "uuid"));
        map.put("audio_path", getStringParameter(request, "audio_path"));
        String movieUrl = getStringParameter(request, "movie_url");
        if (!movieUrl.startsWith("http://aliv.weipai.cn"))
            movieUrl = "http://aliv.weipai.cn/" + movieUrl;
        map.put("file_path", movieUrl);
        map.put("img_3in1", getStringParameter(request, "img_3in1"));
        map.put("need_audio", getIntParameter(request, "is_need_audio"));
        map.put("show_geo", getIntParameter(request, "is_show_gps"));
        map.put("album", getIntParameter(request, "is_from_album"));
        map.put("weipai_video", getIntParameter(request, "is_from_weipai"));
        map.put("custom_cover_num", getIntParameter(request, "custom_cover_num"));
        map.put("address", getStringParameter(request, "record_start_address"));
        Double latitude = getDoubleParameter(request, "publish_start_latitude");
        Double longitude = getDoubleParameter(request, "record_start_longitude");
        map.put("latitude", latitude);
        map.put("longitude", longitude);
        String city = CityUtil.getCityName(latitude, longitude);
        map.put("city", city);

        if (request.getParameter("direction") == null) {
            map.put("direction", 1);
        } else {
            if (request.getParameter("direction").equals("v")) {
                map.put("direction", 1);
            } else {
                map.put("direction", 0);
            }
        }
        return map;
    }

    private void initVideoView(String uid, VideoView videoView) {
        int now = (int) (System.currentTimeMillis() / 1000l);
        videoView.setCreatedAt(now);
        videoView.setActive(Constant.USER_ACTIVE_TYPE.NO.ordinal());
        videoView.setActiveAt(0);
        videoView.setPushed(Constant.USER_PUSH_STATUS.NO.ordinal());
        videoView.setPushedAt(0);
        videoView.setRecommended(Constant.USER_RECOMMENDED_STATUS.NO.ordinal());
        videoView.setRecommendedAt(0);
        videoView.setRecommendedBy("");
        videoView.setActiveBy("");
        videoView.setUser(uid);
        videoView.setPosted(0);
        videoView.setImgLastModified(now);
        videoView.setCatalog("0");
        videoView.setDeleted(0);
    }

    private int getIntParameter(HttpServletRequest request, String fieldName) {
        String fieldStr = request.getParameter(fieldName);
        return fieldStr == null ? 0 : Integer
                .parseInt(fieldStr);
    }

    private Double getDoubleParameter(HttpServletRequest request, String fieldName) {
        String fieldStr = request.getParameter(fieldName);
        return fieldStr == null ? 0.0 : Double.parseDouble(fieldStr);
    }

    private String getStringParameter(HttpServletRequest request, String field) {
        String fieldStr = request.getParameter(field);
        return fieldStr == null ? "" : fieldStr;
    }

    /**
     * 判断视频发布状态
     *
     * @param videoView
     */
    private void judgeVideoPost(VideoView videoView) {
        try {
            // 判断系统状态 是否先发后审
            SystemConfigView systemConfigView = manageService
                    .getSystemConfig(Constant.SYSTEM_CONFIG_ID);
            if (systemConfigView.getVideoActive() == 1) {
                videoView.setPosted(1);
            } else {
                // 非先发后审 判断是否白名单
                WhitelistView whitelistView = manageService.findWhiteUser(videoView.getUser());
                if (whitelistView != null) {
                    videoView.setPosted(1);
                }
            }
        } catch (Exception e) {
            log.error(
                    "----system post or whitelist error!----" + e.getMessage(),
                    e);
        }
    }

    /**
     * 更新视频
     *
     * @param
     * @param vid
     * @param request
     * @throws Exception
     */
    private void updateVideo(String vid, HttpServletRequest request) throws Exception {
        VideoView videoView = videoService.findVideoByVid(vid, null);
        if (videoView != null) {
            Map<String, Object> map = extractVideoMap(request);
            videoService.updateVideo(vid, objectMap2StringMap(map));

            try {
                if (videoView.getPosted() == 1 && videoView.getDeleted() == 0) {
                    VideoSearchView video = new VideoSearchView();
                    VideoStatView videoStatView = statService.findVideoStatByVid(videoView.getVid());
                    BeanUtils.copyProperties(videoView, video);
                    videoService.setStatAttr(video, videoStatView);
                    searchService.updateVideo(video);
                }
            } catch (Exception e) {
                log.error("视频更新搜索索引失败！");
            }
        }
    }

    private Map<String, String> objectMap2StringMap(Map<String, Object> map) {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            result.put(entry.getKey(), entry.getValue().toString());
        }
        return result;
    }

    /**
     * 保存滤镜关系
     *
     * @param request
     */
    private void saveEffectView(VideoView videoView, HttpServletRequest request) {

        try {
            String effectsList = request.getParameter("effects_list");
            if (!StringUtils.isEmpty(effectsList.trim())) {
                List<EffectView> effectList = new ArrayList<EffectView>();
                List<Map<String, Object>> list = JacksonUtil.readJsonToObject(
                        List.class, effectsList);
                if (list != null) {
                    for (Map<String, Object> map : list) {
                        EffectView effectView = new EffectView();
                        effectView.setVid(videoView.getVid());
                        effectView.setEffectName(map.get("effectName")
                                .toString());
                        effectView.setEndTimeInterval(map
                                .get("endTimeInterval").toString());
                        effectView.setStartTimeInterval(map.get(
                                "startTimeInterval").toString());
                        effectView.setEffectID(Integer.parseInt(map.get(
                                "effectID").toString()));
                    }
                }

                videoService.saveEffectList(effectList);
            }
        } catch (Exception e) {
            log.error("----video effect save error !----" + e.getMessage(), e);
        }
    }

    /**
     * 保存视频关系表
     *
     * @param
     * @param
     */
    private void saveUserVideo(VideoView videoView) {
        int now = (int) (System.currentTimeMillis() / 1000l);
        try {
            UserVideoView userVideoView = new UserVideoView();
            userVideoView.setUid(videoView.getUser());
            userVideoView.setVid(videoView.getVid());
            userVideoView.setTime(now);
            userVideoView.setPlayTimes(0);
            userVideoView.setDeleted(0);
            userVideoView.setDelBy("");

            relationService.saveUserVideo(userVideoView);
        } catch (Exception e) {
            log.error("----saveUserVideo error!----" + e.getMessage(), e);
        }
    }

    /**
     * 保存uuid关系表
     *
     * @param videoView
     */
    private void saveVideoRelation(VideoView videoView) {
        try {
            VideoRelationView videoRelationView = new VideoRelationView();
            videoRelationView.setUuid(videoView.getUuid());
            videoRelationView.setVid(videoView.getVid());
            videoRelationView.setCreate_at(videoView.getCreatedAt());
            // 保存uuid关系表
            videoService.saveVideoRelation(videoRelationView);
        } catch (Exception e) {
            log.error("----saveVideoRelation error!----" + e.getMessage(), e);
        }
    }

    public void saveVideoIndex(VideoView videoView,
                               HttpServletRequest request) {
        // 保存videoindex 表
        try {
            VideoIndexView videoIndex = new VideoIndexView();

            videoIndex.setVid(videoView.getVid());
            videoIndex.setUid(videoView.getUser());
            int userVideoCount = relationService.findUserVideoCountByUid(videoView.getUser());
            if (userVideoCount <= 0) {
                videoIndex.setFirstUpload(0);
            } else {
                videoIndex.setFirstUpload(1);
            }
            videoIndex.setCreatedAt(videoView.getCreatedAt());
            videoIndex.setActive(videoView.getActive());
            videoIndex.setPool(0);
            videoIndex.setDeleted(videoView.getDeleted());
            videoIndex.setTotalPlay(0);
            videoIndex.setWeekPlay(0);
            videoIndex.setMonthPlay(0);
            videoIndex.setTotal(0);
            videoIndex.setPopular(0);
            videoIndex.setPushed(0);
            videoIndex.setPushedAt(0);
            videoIndex.setRankStatus(0);
            String ip = IPUtil.getIP(request);
            Map<String, Integer> cityMap = CityUtil.getCityNumDetail(
                    videoView.getLongitude(), videoView.getLatitude(), ip);
            videoIndex.setProvince(cityMap.get("province"));
            videoIndex.setCity(cityMap.get("city"));
            videoIndex.setDeleted(0);
            videoIndex.setNormalTp(0);
            videoIndex.setPornTp(0);

            indexService.saveVideoIndex(videoIndex);

        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * 拆分list分页
     *
     * @param
     * @param
     * @param
     * @return
     */
    // private List<DefenderParam> opratePage(String uid,final
    // List<DefenderParam> totalList,
    // int start, int count) {
    // List<DefenderParam> splitList = new ArrayList<DefenderParam>();
    // try {
    // List<DefenderParam> list = new LocalCache<List<DefenderParam>>() {
    //
    // @Override
    // public List<DefenderParam> getAliveObject() throws Exception {
    // List<DefenderParam> cacheList = new ArrayList<DefenderParam>();
    // for(DefenderParam df:totalList){
    // if(isForbidden(df.getUid(),
    // Constant.USER_AUTH_FORBID.GUARD_RANK.ordinal())){
    // cacheList.add(df);
    // }
    // }
    // return cacheList;
    // }
    // }.put(60*10, "defender_"+uid+"_"+start);
    // if (totalList != null) {
    // int total = totalList.size();
    // if (total > (start + count)) {
    // splitList = new ArrayList<DefenderParam>(list.subList(
    // start, start + count));
    // } else if(total>=start&&total<=(start + count)){
    // splitList = new ArrayList<DefenderParam>(list.subList(
    // start, total));
    // }
    // }
    // } catch (Exception e) {
    // log.error(e.getMessage(), e);
    // }
    //
    // return splitList;
    // }
    @RequestMapping(value = "/delete_blog")
    @ResponseBody
    public Map deleteBlog(
            @RequestParam(required = true, value = "blog_id") String vid
    ) throws ReturnException {

        if (StringUtils.isEmpty(vid)) {
            return error("1002");
        }

        // 取得当前登录用户
        String currentUser = XThreadLocal.getInstance().getCurrentUser();
        if (currentUser == null) {
            return error("2019");
        }
        // 分别从index、video、relation、search、feed中删除video相关信息
        videoStory.deleteVideo(currentUser, vid);

        return success();
    }

    /**
     * 修改视频封面
     *
     * @return
     */
    @RequestMapping(value = "/upload_screenshoot")
    @ResponseBody
    public Map<String, String> upload_screenshoot(
            @RequestParam("screenshoot_file") MultipartFile screenshootFile,
            @RequestParam("vid") String vid)
            throws ServiceException, ReturnException {

        if (vid == null) {
            return error("2019");
        }

        Map<String, String> result = new HashMap<>();
        if (screenshootFile.isEmpty()) {
            return error("3001");
        }

        String ext = FilenameUtils.getExtension(
                screenshootFile.getOriginalFilename()).toLowerCase();
        if (isForbidden(XThreadLocal.getInstance().getCurrentUser(), Constant.USER_AUTH_FORBID.CHANGE_AVATAR.getIndex())) {
            return error("5002");
        }

        VideoView video = videoService.findVideoByVid(vid, null);
        if (!video.getUser().equals(XThreadLocal.getInstance().getCurrentUser())) {
            return error("5003");
        }

        if (ext != null && !"".equals(ext)) {
            if (!Arrays.asList("jpg", "jpeg", "bmp", "png", "gif", "webp")
                    .contains(ext)) {
                return error("3003");
            }
        }
        // 获取指定文件的输入流
        InputStream content = null;
        try {
            content = screenshootFile.getInputStream();
        } catch (IOException e) {
            return error("3004");
        }

        // 初始化OSSClient
        OSSClient client = new OSSClient(Loader.getInstance().getProps(
                "AliOSS.accessKeyId"), Loader.getInstance().getProps(
                "AliOSS.accessKeySecret"));
        // 创建上传Object的Metadata
        ObjectMetadata meta = new ObjectMetadata();

        // 必须设置ContentLength
        long size = screenshootFile.getSize();
        meta.setContentLength(size);
        String date = new SimpleDateFormat("YMM/dd/HH/").format(new Date());
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
        String path = "screenshoot/" + date + vid + "-" + timestamp + ".jpg";

        // 上传Object.
        client.putObject(Loader.getInstance()
                .getProps("AliOSS.bucketName"), path, content, meta);

        String url = "http://aliv.weipai.cn/" + path;

        result.put("default_img", url);
        videoService.updateVideo(vid, result);

        return success(result);
    }

    public static void main(String[] args) {
//		String a = "1443171795.3816051";
//
//		System.out.println(Double.parseDouble(a));
        //	System.out.println(System.currentTimeMillis()/1000);

        Map<String, Object> map = GpsUtil.getUserGps(Double.parseDouble("43.5149"), Double.parseDouble("124.8197"));
        List aList = new ArrayList();
        for (int i = 0; i < 13; i++) {
            aList.add(i);
        }
        System.out.println(aList);
        int size = aList.size();
        if (size > 10 && size / 2 > 0) {
            aList.remove(size - 1);
        }
        System.out.println(aList);
        System.out.println(14 % 2);
        System.out.println(13 % 2);
    }

}