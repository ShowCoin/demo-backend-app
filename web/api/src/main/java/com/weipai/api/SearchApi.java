package com.weipai.api;

import com.weipai.annotation.LoginRequired;
import com.weipai.application.SearchStory;
import com.weipai.form.PageForm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;

@Controller
public class SearchApi extends BaseApi {
    @Autowired
    private SearchStory searchStory;

    /**
     * 用户、视频搜索
     *
     * @param pageForm
     * @param keyword
     * @param currentUser
     * @return
     */
    @RequestMapping(value = "/search")
    @ResponseBody
    @LoginRequired
    public Map search(
            @Validated PageForm pageForm,
            @RequestParam("keyword") String keyword,
            @RequestHeader("Weipai-Userid") String currentUser
    ) throws Exception {
        // 关键字为空时，不执行搜索
        keyword = sanitize(keyword);
        if (keyword.equals("")) return success();

        Map<String, Object> result = new HashMap<>();

        List<Map<String, Object>> videoSearchList = searchStory.searchVideo(keyword, pageForm);
        result.put("video_list", videoSearchList);
        result.put("next_cursor", pageForm.getNextCursor(videoSearchList));

        pageForm.setCount(4);
        result.put("user_list", searchStory.searchUser(keyword, pageForm, currentUser));

        return success(result);
    }

    /**
     * 用户搜索
     *
     * @param keyword
     * @param pageForm
     * @param currentUser
     * @return
     */
    @RequestMapping(value = "/search_user")
    @ResponseBody
    @LoginRequired
    public Map searchUser(
            @Validated final PageForm pageForm,
            @RequestParam("keyword") String keyword,
            @RequestHeader("Weipai-Userid") String currentUser
    ) throws Exception {
        // 关键字为空时，不执行搜索
        keyword = sanitize(keyword);
        if (keyword.equals("")) return success();

        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> userSearchList = searchStory.searchUser(keyword, pageForm, currentUser);
        result.put("user_list", userSearchList);
        result.put("next_cursor", pageForm.getNextCursor(userSearchList));

        return success(result);
    }

    /**
     * 视频搜索
     *
     * @param keyword
     * @param pageForm
     * @return
     */
    @RequestMapping(value = "/search_video")
    @ResponseBody
    @LoginRequired
    public Map search_video(
            @Validated final PageForm pageForm,
            @RequestParam("keyword") String keyword
    ) throws Exception {
        // 关键字为空时，不执行搜索
        keyword = sanitize(keyword);
        if (keyword.equals("")) return success();

        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> videoSearchList = searchStory.searchVideo(keyword, pageForm);
        result.put("video_list", videoSearchList);
        result.put("next_cursor", pageForm.getNextCursor(videoSearchList));

        return success(result);

    }

    /**
     * 热门主播
     *
     * @return
     */
    @RequestMapping(value = "/search/hot_people")
    @ResponseBody
    @LoginRequired
    public Map hotPeople() throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("hot_people", searchStory.findHotPeople());
        return success(result);
    }

    /**
     * 热门关键字
     *
     * @return
     */
    @RequestMapping(value = "/search/hot_keywords")
    @ResponseBody
    @LoginRequired
    public Map hotKeywords() throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("hot_keywords", asList("热舞", "才艺", "女神", "性感", "福利"));
        return success(result);
    }

    private String sanitize(@RequestParam(value = "keyword") String keyword) {
        keyword = keyword.replace(" ", "");
        while (keyword.startsWith("*") || keyword.startsWith("?"))
            keyword = keyword.substring(1);
        return keyword;
    }

}
