package com.weipai.api;

import com.weipai.common.AreaNetCache;
import com.weipai.common.GpsUtil;
import com.weipai.common.IPSeeker;
import com.weipai.common.exception.ReturnException;
import com.weipai.common.exception.ServiceException;
import com.weipai.gps.thrift.view.GpsView;
import com.weipai.init.WPInit;
import com.weipai.interceptor.XThreadLocal;
import com.weipai.service.GpsService;
import com.weipai.service.UserService;
import com.weipai.user.thrift.view.UserView;
import com.weipai.util.CityUtil;
import com.weipai.util.IpComparator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

@Controller
public class GpsApi extends BaseApi{
	private static final Logger log = LoggerFactory.getLogger(GpsApi.class);
	private IPSeeker ipsk;
	public void init() throws ServletException {
		ipsk = new IPSeeker();
	}
	@Autowired
	GpsService gpsService;
	@Autowired
	UserService userService;

	@RequestMapping("/updateGPS")
	@ResponseBody
	public Map updateGps(@RequestParam(required=false,value="uid")String uid,@RequestParam(required=true,value="lng")String lng,@RequestParam(required=true,value="lat")String lat) throws ReturnException {
		String userId=XThreadLocal.getInstance().getCurrentUser();
		if (userId == null){
			return error("2019");
		}

		UserView user = getUserView(userId);
		if(lng!=null&&lat!=null){
			if(lng.indexOf("+")==0){
				lng = lng.replaceAll("\\+", "");
			}
			if(lat.indexOf("+")==0){
				lat = lat.replaceAll("\\+", "");
			}
		}

		if (lng != null && !"".equals(lng) && Double.valueOf(lng)>0 && lat != null
				&& !"".equals(lat) && Double.valueOf(lat)>0) {
			GpsView gps = null;
			try {
				gps=gpsService.findGpsByUid(userId);
			} catch (ServiceException e1) {
				log.error(e1.getMessage(),e1);
			}
			double lati = Double.valueOf(lat).doubleValue();
			double lngi = Double.valueOf(lng).doubleValue();
//			Map<String,Object> map=GpsUtil.getUserGps(lati,lngi);
//			Map<String,Object> map1 = (Map<String, Object>) map.get("result");
//			Map<String,Object> address=(Map<String,Object>)map1.get("addressComponent");
//			String city=(String) address.get("city");
			String city = CityUtil.getCityName(Double.parseDouble(lat),Double.parseDouble(lng));
			if(gps==null){
				gps = new GpsView();
				gps.setCity(city);
				gps.setLatitude(lati);
				gps.setLongitude(lngi);
				gps.setUpdateTime((int)(System.currentTimeMillis()/1000));
				if(user.getSex4Display()!=null){
					gps.setGender(user.getSex4Display());
				}
				gps.setUid(userId);
				try {
					gpsService.saveGps(gps);
				} catch (ServiceException e) {
					log.error(e.getMessage(), e);
				}
			}else{
				Map<String,String> params = new HashMap<String,String>();
				params.put("longitude", lng);
				params.put("latitude", lat);
				if(user.getSex4Display()!=null){
					params.put("gender", user.getSex4Display());
				}
				params.put("update_time", String.valueOf((int)(System.currentTimeMillis()/1000)));
				params.put("city", city);
				try {
					gpsService.updateGps(userId, params);
				} catch (Exception e) {
					log.error(e.getMessage(),e);
				}
			}

			try {
				Map<String,String> params = new HashMap<String,String>();
				params.put("lng", lng);
				params.put("lat", lat);
				params.put("update_time", String.valueOf((int)(System.currentTimeMillis()/1000)));
				userService.updateUserById(userId, params);
			} catch (ServiceException e) {
				log.error(e.getMessage(), e);
			}
		}
		return success();
	}
	/**
	 * 附近的人
	 * @param lng 经度
	 * @param lat 纬度
	 * @param raidus 范围 单位米
	 * @param current 当前条数
	 * @param pageCount 每页显示条数
	 * @return
	 */
	@RequestMapping("/near_user")
	@ResponseBody
	public Map getUserNear(@RequestParam(required=false,value="raidus")String raidus,@RequestParam(required=false,value="current")String current,@RequestParam(required=true,value="count")String pageCount,HttpServletRequest request,@RequestParam(required=false,value="relative")String relative){
		final Map<String,String> map = new HashMap<String,String>();
		//final String uid = XThreadLocal.getInstance().getCurrentUser();
		String lng = request.getHeader("Longitude");
		if(lng.indexOf("+")==0){
			lng = lng.replaceAll("\\+", "");
		}
		String lat=request.getHeader("Latitude");
		if(lat.indexOf("+")==0){
			lat = lat.replaceAll("\\+", "");
		}
		final String currentlng = lng;

		final String currentlat = lat;
		Map<String,Object> result = new HashMap<String,Object>();
		try {
			if(raidus==null || "".equals(raidus)){
				raidus = "100000";
			}
			double [] jws = GpsUtil.getAround(Double.valueOf(currentlat).doubleValue(),Double.valueOf(currentlng).doubleValue(),Integer.valueOf(raidus).intValue());
			double minLat=jws[0];
			double minLng=jws[1];
			double maxLat=jws[2];
			double maxLng=jws[3];
			map.put("left_lat", String.valueOf(minLat));
			map.put("right_lat", String.valueOf(maxLat));
			map.put("down_lon", String.valueOf(minLng));
			map.put("top_lon", String.valueOf(maxLng));
			map.put("top_lon", String.valueOf(maxLng));
			//map.put("uid", String.valueOf(uid));
			map.put("lat", currentlat);
			map.put("lon", currentlng);
			if(current==null||"".equals(current)){
				current="0";
			}
			final String start=current;
			final String count=pageCount;
			final List<GpsView> gpsViews  = gpsService.findNearGpsList(map,Integer.parseInt(start),Integer.parseInt(count));
			log.info("-------gpsViews---------"+gpsViews);
			if(gpsViews!=null){
				List<Map<String,Object>> nearGpsList = new ArrayList<Map<String,Object>>();

				for(final GpsView gpsView:gpsViews){
					UserView userView = userService.findUserById(gpsView.getUid());
					if(userView==null){
						continue;
					}
					Map<String,Object> params = new HashMap<String,Object>();
					int distance=(int) Math.floor(GpsUtil.distance(Double.parseDouble(currentlng), Double.parseDouble(currentlat), gpsView.getLongitude(), gpsView.getLatitude()));
					params.put("distance", String.valueOf(distance));
					params.put("uid", userView.getId());
					params.put("nickName", userView.getNickname());
					params.put("sex", userView.getSex4Display());
					params.put("profileImg", userView.getProfileImg());
					nearGpsList.add(params);
				}


				result.put("result", nearGpsList);
			}

		} catch (ServiceException e) {
			log.error(e.getMessage(),e);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		return success(result);
	}
	@RequestMapping("/city_list")
	public Map cityList(){
		Map<String,Object> map = new HashMap<String,Object>();
		IpComparator ipc = new IpComparator();
		List<ConcurrentHashMap<String, Object>> cityList = (List<ConcurrentHashMap<String, Object>>)WPInit.city.get("cityList");
		Collections.sort(cityList,ipc);
		map.put("cityList",cityList);
		return success(map);
	}
	public void iptest(){
		String net_area = ipsk.getIpgeter().search("192.168.1.164");
		if (net_area != null) {

			String[] net_area_s = net_area.split(",");
			int net = Integer.valueOf(net_area_s[0]);
			int area = Integer.valueOf(net_area_s[1]);

			String shi = AreaNetCache.areaCache.get(area);
			String sheng = "";
			if (area >= 100) {
				sheng = AreaNetCache.areaCache.get(area / 100);
			} else {
				sheng = shi;
			}
			String wangluo = AreaNetCache.netCache.get(net);

			StringBuilder str_rs = new StringBuilder();
			str_rs.append("SourceIP：");
			str_rs.append("192.168.1.164");
			str_rs.append(" --> NettypeID，AredID = ");
			str_rs.append(net);
			str_rs.append("，");
			str_rs.append(area);
			str_rs.append("  [");
			str_rs.append(sheng);
			str_rs.append("：");
			str_rs.append(shi);
			str_rs.append("，");
			str_rs.append(wangluo);
			str_rs.append("]");
			System.out.println(str_rs);
		}
	}
	public static void main(String[] args) {
		double [] jws = GpsUtil.getAround(Double.valueOf("39.995662").doubleValue(),Double.valueOf("116.473092").doubleValue(),Integer.valueOf("100000").intValue());
		double minLat=jws[0];
		double minLng=jws[1];
		double maxLat=jws[2];
		double maxLng=jws[3];System.out.println(minLat+"----"+minLng+"--------"+maxLat+"-------"+maxLng);
	}
}
