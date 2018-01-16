/**
 *
 */
package com.weipai.api;

import com.weipai.application.ChannelDTO;
import com.weipai.application.ChannelStory;
import com.weipai.common.Constant;
import com.weipai.common.Constant.USER_AGENT;
import com.weipai.common.Utils;
import com.weipai.common.Version;
import com.weipai.common.cache.LocalCache;
import com.weipai.common.cache.RemoteCache;
import com.weipai.common.exception.ServiceException;
import com.weipai.init.WPInit;
import com.weipai.interceptor.XThreadLocal;
import com.weipai.manage.thrift.view.AdvertisementView;
import com.weipai.manage.thrift.view.SystemConfigView;
import com.weipai.service.GiftService;
import com.weipai.service.ManageService;
import com.weipai.service.PayService;
import com.weipai.service.RankService;
import com.weipai.user.thrift.view.UserView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.servlet.http.HttpServletResponse;

/**
 */
@Controller
public class ConfigApi extends BaseApi {

    private static final Logger log = LoggerFactory.getLogger(ConfigApi.class);

    private static final String EFFECT_URL = "";
    private static final String REDPACKET_URL = "";
    @Autowired
    private PayService payService;
    @Autowired
    private RankService rankService;
    @Autowired
    private ChannelStory channelStory;
    @Autowired
    private ManageService manageService;
    @Autowired
    private GiftService giftService;

    /**
     * @return
     */
    @RequestMapping("/config")
    @ResponseBody
    public Map config(
            @RequestHeader(value = "Device-Uuid", required = false) String deviceUuid
            , @RequestHeader(value = "Client-Version", required = false) final String clientVersion
            , @RequestHeader("Channel") String channel
    ) {

        Map<String, Object> resultMap = new HashMap<>();

        SystemConfigView systemConfigView = manageService.getSystemConfigView(
                clientVersion);
        final USER_AGENT userAgent = XThreadLocal.getInstance().getUserAgent();
        final boolean isFake = manageService.isFake(userAgent
                , clientVersion, channel);

        setServerInfo(deviceUuid, resultMap);
        setSwitch(resultMap, systemConfigView, isFake);
        setChannelInfo(resultMap, isFake);
        setOtherConfig(resultMap, systemConfigView);
        return success(resultMap);
    }

    private void setChannelInfo(Map<String, Object> resultMap, boolean isFake) {
        Map<String, Object> map;
        if (isFake) {
            map = new HashMap<>();
            List<ChannelDTO> channelDTOs = new ArrayList<>();
            channelDTOs.add(new ChannelDTO(1, "直播", 3));
            channelDTOs.add(new ChannelDTO(2, "热门", 2));
            map.put("channels", channelDTOs);
            map.put("defaultChannelIndex", 1);
        } else {
            map = channelStory.findEnableChannelsWithDefaultMap();
        }
        resultMap.putAll(map);
    }

    private void setOtherConfig(Map<String, Object> resultMap, SystemConfigView systemConfigView) {
        resultMap.put("videoDefinition", systemConfigView.getVideoDefinition());
        resultMap.put("videoFrame", systemConfigView.getVideoFrame());
        resultMap.put("shareSina", systemConfigView.getShareSina());
        resultMap.put("shareQq", systemConfigView.getShareQq());
        resultMap.put("shareQqzone", systemConfigView.getShareQqzone());
        resultMap.put("shareWx", systemConfigView.getShareWx());
        resultMap.put("shareWxtimeline", systemConfigView.getShareWxtimeline());
        resultMap.put("show_ad", String.valueOf(systemConfigView.getShowAd()));
        resultMap.put("min_withdraw_gold", 10000);
        resultMap.put("withdraw_unit", 1000);
        resultMap.put("gift_price", 100);
        resultMap.put("paid_comment_price", 10);
        resultMap.put("effectResource", findCurrentGiftEffectLink());
        resultMap.put("redPacketUrl", REDPACKET_URL);
    }

    private String findCurrentGiftEffectLink() {
        try {
            return new LocalCache<String>() {
                @Override
                public String getAliveObject() throws Exception {
                    return new RemoteCache<String>() {
                        @Override
                        public String getAliveObject() throws Exception {
                            return giftService.findCurrentGiftEffectLink();
                        }
                    }.put(10 * 60, "weipai:api:config:effectResource-http://aliv.weipai.cn/gift/effectZip/~/~/~/~.zip");
                }
            }.put(10 * 60, "weipai:api:config:effectResource-http://aliv.weipai.cn/gift/effectZip/~/~/~/~.zip");
        } catch (Exception e) {
            log.error("加载礼物特效资源包失败！Exception:{}", e);
            return EFFECT_URL;
        }
    }

    private void setSwitch(Map<String, Object> resultMap, SystemConfigView systemConfigView, boolean isFake) {
        Map<String, Object> switchMap = new HashMap<>();

        switchMap.put("alipay", isFake ? 0 : 1);
        switchMap.put("weixinpay", isFake ? 0 : 1);
        switchMap.put("review_new_video", 0);
        switchMap.put("isfilter", systemConfigView.getIsfilter());
        switchMap.put("comment_action", 0);
        switchMap.put("charge_tab", 0);
        switchMap.put("im", 1);
        resultMap.put("switch", switchMap);
    }

    private void setServerInfo(@RequestHeader(value = "Device-Uuid", required = false) String deviceUuid, Map<String, Object> resultMap) {
        List<String> serverList = WPInit.globalConf.getUploadAddress();
        int uploadServerIndex;
        if (deviceUuid != null) {
            uploadServerIndex = (int) Utils.string2numberHash(deviceUuid, serverList.size());
        } else {
            uploadServerIndex = new Random().nextInt(serverList.size());
        }
        resultMap.put("http_server", "http://w1.weipai.cn");
        resultMap.put("flip_server", "http://flip.weipai.cn");
        resultMap.put("http_upload_server", serverList.get(uploadServerIndex));
        resultMap.put("pm_server", Constant.WEIPAI_PM_SERVER);
        resultMap.put("apns_server", "http://w1.weipai.cn");
        resultMap.put("pay_server", "https://pay.weipai.cn");
    }

    @RequestMapping("/zhifubao_switch")
    @ResponseBody
    public Map Zhifubao_switch(@RequestHeader(value = "Client-Version", required = false) final String clientVersion
    ) {
        SystemConfigView systemConfigView = manageService.getSystemConfigView(clientVersion);
        if (systemConfigView.getAlipay() == 0) {
            Map<String, String> result = new HashMap<>();
            result.put("state", "0");
            return result;
        }
        return success();
    }

    @RequestMapping("/get_url")
    @ResponseBody
    public Map getUrl(@RequestParam(required = true, value = "url_type") String urlType) {
        Map<String, Object> map = new HashMap<String, Object>();

        map.put("url", "http://www.weipai.cn/account/forget");
        return success(map);
    }

    @RequestMapping("/get_advertisement")
    @ResponseBody
    public Map getAdvertisement(
            @RequestHeader("Client-Version") Version clientVersion
            , @RequestHeader("Channel") String channel
            , @RequestParam("type") final String type
    ) {
        Map<String, Object> map = new HashMap<>();
        List<Map<String, Object>> resultList = new ArrayList<>();
        final USER_AGENT userAgent = XThreadLocal.getInstance().getUserAgent();
        final boolean isFake = manageService.isFake(userAgent
                , clientVersion.toString(), channel);
        if (!isFake) {
            getAd(type, resultList);
        }
        map.put("adlist", resultList);
        return success(map);
    }

    private void getAd(final String type, List<Map<String, Object>> resultList) {
        try {
            List<AdvertisementView> list = new LocalCache<List<AdvertisementView>>() {

                @Override
                public List<AdvertisementView> getAliveObject() throws Exception {
                    return manageService.findAdvertisementByUiType(Integer.valueOf(type));
                }
            }.put(60 * 30, "advs_" + type);

            final USER_AGENT userAgent = XThreadLocal.getInstance().getUserAgent();
            int os = 0;
            switch (userAgent) {
                case ANDROID:
                    os = 0b0100;
                    break;
                case IOS:
                    os = 0b1000;
                    break;
                default:
                    break;
            }

            if (list != null) {
                for (AdvertisementView ad : list) {
                    if ((ad.getCreateTime() & 0b0010) > 0 && (ad.getCreateTime() & os) > 0) {

                        String uid = XThreadLocal.getInstance().getCurrentUser();

                        Map<String, Object> resultMap = new HashMap<>();
                        resultMap.put("img", ad.getImg());
                        String url = ad.getUrl();
                        if ((ad.getCreateTime() & 0b10000) > 0 && (uid != null)) {

                            UserView userView = getUserView(uid);
                            if (userView != null && userView.getNickname() != null) {
                                if (url.indexOf("?") == -1) {
                                    url += "?";
                                } else {
                                    url += "&";
                                }
                                url += "uid=" + uid + "&nickname=" + URLEncoder.encode(userView.getNickname(), "UTF-8");
                            }
                        }
                        resultMap.put("url", url);
                        resultMap.put("title", ad.getTitle());
                        resultList.add(resultMap);
                    }
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }


    /**
     * @return
     * @throws ServiceException
     */
    @RequestMapping(value = "/plaza_image_link_list")
    @ResponseBody
    public Map<String, Object> plaza_image_link_list(
            @RequestParam(value = "name", required = false) String tagname, @RequestParam(value = "", required = false) String gid, HttpServletResponse response)
            throws ServiceException {
        Map<String, Object> result = new HashMap<>();

        response.setContentType("text/html; charset=UTF-8");

        String appName = XThreadLocal.getInstance().getHeaderParams().getAppName();

        if (appName != null && appName.equals("weipai light")) {
            result.put("imagelink_list", new ArrayList<>());
            return success(result);
        }

        int type = 1;
        if (gid != null && !"".equals(gid)) {
            type = Integer.valueOf(gid).intValue();
        }
        final int t = type;
        try {
            List<AdvertisementView> advs = new LocalCache<List<AdvertisementView>>() {

                @Override
                public List<AdvertisementView> getAliveObject() throws Exception {
                    return manageService.findAdvertisementByUiType(t);
                }
            }.put(60 * 30, "advs_" + type);
            List<Map<String, Object>> resultList = new ArrayList<Map<String, Object>>();
            USER_AGENT userAgent = XThreadLocal.getInstance().getUserAgent();
            int osMask = 10;
            switch (userAgent) {
                case ANDROID:
                    osMask = 0b0100;
                    break;
                case IOS:
                    osMask = 0b1000;
                    break;
                default:
                    break;
            }

            String clientVersion = XThreadLocal.getInstance().getHeaderParams().getClientVersion();
            int versionMask;

            if (clientVersion != null && clientVersion.compareTo("5.0") >= 0) {
                versionMask = 0b0010;
            } else {
                versionMask = 0b0001;
            }

            if (advs != null) {
                for (AdvertisementView adv : advs) {
                    if ((adv.getCreateTime() & versionMask) > 0 && (adv.getCreateTime() & osMask) > 0) {

                        String uid = XThreadLocal.getInstance().getCurrentUser();

                        Map<String, Object> map = new HashMap<String, Object>();
                        map.put("image_url", adv.getImg());

                        String url = adv.getUrl();
                        if ((adv.getCreateTime() & 0b10000) > 0 && (uid != null)) {
                            UserView userView = getUserView(uid);
                            if (userView != null && userView.getNickname() != null) {
                                if (url.indexOf("?") == -1) {
                                    url += "?";
                                } else {
                                    url += "&";
                                }
                                url += "uid=" + uid + "&nickname=" + URLEncoder.encode(userView.getNickname(), "UTF-8");
                            }
                        }

                        map.put("link_url", url);
                        map.put("link_title", adv.getTitle());
                        map.put("link_needzoom", "1");
                        resultList.add(map);
                    }
                }
            }
            result.put("imagelink_list", resultList);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return success(result);
    }
}


