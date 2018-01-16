package com.weipai.api;

import com.weipai.application.activity.WeiPaiActivityStory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/**
 */
@Controller
@RequestMapping("/activity")
public class ActivityApi extends BaseApi {
    private final WeiPaiActivityStory weiPaiActivityStory;

    @Autowired
    public ActivityApi(WeiPaiActivityStory weiPaiActivityStory) {
        this.weiPaiActivityStory = weiPaiActivityStory;
    }

    /**
     * 活动分享接口
     *
     * @param url
     * @param platform
     * @param currentUser
     * @return
     */
    @RequestMapping(value = "/share", method = RequestMethod.POST)
    @ResponseBody
    public Map share(@RequestParam("url") String url,
                     @RequestParam("platform") String platform,
                     @RequestHeader(value = "Weipai-Userid", required = false) String currentUser
    ) {
        String key = "weipai:api:activity_share:currentUser-" + currentUser + ":url-" + url + ":platform-" + platform;
        return success();
    }
}
