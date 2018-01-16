package com.weipai.api;

import com.weipai.annotation.LoginRequired;
import com.weipai.application.ChannelMediaDTO;
import com.weipai.application.ChannelStory;
import com.weipai.common.Version;
import com.weipai.common.exception.ReturnException;
import com.weipai.form.PageForm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 */

@Controller
@RequestMapping("/channel")
public class ChannelApi extends BaseApi {

    private final ChannelStory channelStory;

    @Autowired
    public ChannelApi(ChannelStory channelStory) {
        this.channelStory = channelStory;
    }

    /**
     * 最新频道
     *
     * @param pageForm
     * @return
     */
    @RequestMapping(value = "/detail", method = RequestMethod.GET)
    @ResponseBody
    @LoginRequired
    public Map findLatestMedium(
            @Validated PageForm pageForm
    ) throws ReturnException {
        List<ChannelMediaDTO> channelMediaDTOs = channelStory.findLatestMedium(pageForm.getCursor(), pageForm.getCount());
        Map<String, Object> data = new HashMap<>();
        data.put("medium", channelMediaDTOs);
        data.put("next_cursor", pageForm.getNextCursor(channelMediaDTOs));
        return success(data);
    }

    /**
     * 频道详情（可配置）
     *
     * @param pageForm    分页条件
     * @param id          频道编号
     * @param rand        随机标记——0：翻页，1：随机
     * @param version     版本号
     * @param currentUser 登录用户ID
     * @return
     */
    @RequestMapping(value = "/content", method = RequestMethod.GET)
    @ResponseBody
    @LoginRequired
    public Map findChannelDetails(
            @Validated PageForm pageForm,
            @RequestParam("id") Integer id,
            @RequestParam("rand") Integer rand,
            @RequestHeader("Client-Version") Version version,
            @RequestHeader("Weipai-Userid") String currentUser,
            @RequestHeader("Channel") String channel
    ) throws Exception {
        return success(channelStory.findChannelDetails(id, 0, pageForm
                , currentUser, version, 1, channel));
    }
}
