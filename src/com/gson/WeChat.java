/**
 * 微信公众平台开发模式(JAVA) SDK
 * (c) 2012-2013 ____′↘夏悸 <wmails@126.cn>, MIT Licensed
 * http://www.jeasyuicn.com/wechat
 */
package com.gson;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.gson.bean.Articles;
import com.gson.bean.Attachment;
import com.gson.bean.InMessage;
import com.gson.bean.OutMessage;
import com.gson.inf.MessageProcessingHandler;
import com.gson.oauth.Menu;
import com.gson.oauth.Message;
import com.gson.util.ConfKit;
import com.gson.util.HttpKit;
import com.gson.util.Tools;
import com.gson.util.XStreamFactory;
import com.thoughtworks.xstream.XStream;

/**
 * 微信常用的API
 *
 * @author L.cm & ____′↘夏悸
 * @date 2013-11-5 下午3:01:20
 */
public class WeChat {
    public static final String ACCESSTOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential";
    public static final String PAYFEEDBACK_URL = "https://api.weixin.qq.com/payfeedback/update";
    public static final String DEFAULT_HANDLER = "com.gson.inf.DefaultMessageProcessingHandlerImpl";
    public static final String USER_INFO_URL = "https://api.weixin.qq.com/cgi-bin/user/info?access_token=";
    public static final String GET_MEDIA_URL= "http://file.api.weixin.qq.com/cgi-bin/media/get?access_token=";
    public static final String UPLOAD_MEDIA_URL= "http://file.api.weixin.qq.com/cgi-bin/media/upload?access_token=";
    
    private static MessageProcessingHandler messageProcessingHandler = null;
    /**
     * 消息操作接口
     */
    public static final Message message = new Message();
    /**
     * 菜单操作接口
     */
    public static final Menu menu = new Menu();

    /**
     * @throws IOException 
     * @throws NoSuchProviderException 
     * @throws NoSuchAlgorithmException 
     * @throws KeyManagementException 
     * 获取access_token
     *
     * @param @return 设定文件
     * @return String    返回类型
     * @throws
     */
    public static String getAccessToken() throws KeyManagementException, NoSuchAlgorithmException, NoSuchProviderException, IOException {
        String appid = ConfKit.get("AppId");
        String secret = ConfKit.get("AppSecret");
        String jsonStr = HttpKit.get(ACCESSTOKEN_URL.concat("&appid=") + appid + "&secret=" + secret);
        Map<String, Object> map = JSONObject.parseObject(jsonStr);
        return map.get("access_token").toString();
    }

    /**
     * 支付反馈
     * @param openid
     * @param feedbackid
     * @return
     * @throws IOException 
     * @throws NoSuchProviderException 
     * @throws NoSuchAlgorithmException 
     * @throws KeyManagementException 
     */
    public static boolean payfeedback(String openid, String feedbackid) throws KeyManagementException, NoSuchAlgorithmException, NoSuchProviderException, IOException {
        Map<String, String> map = new HashMap<String, String>();
        String accessToken = getAccessToken();
        map.put("access_token", accessToken);
        map.put("openid", openid);
        map.put("feedbackid", feedbackid);
        String jsonStr = HttpKit.get(PAYFEEDBACK_URL, map);
        Map<String, Object> jsonMap = JSONObject.parseObject(jsonStr);
        return "0".equals(jsonMap.get("errcode").toString());
    }

    /**
     * 签名检查
     * @param token
     * @param signature
     * @param timestamp
     * @param nonce
     * @return
     */
    public static Boolean checkSignature(String token, String signature, String timestamp, String nonce) {
        return Tools.checkSignature(token, signature, timestamp, nonce);
    }

    /**
     * 根据接收到用户消息进行处理
     * @param responseInputString   微信发送过来的xml消息体
     * @return
     */
    public static String processing(String responseInputString) {
        InMessage inMessage = parsingInMessage(responseInputString);
        OutMessage oms = new OutMessage();
        // 加载处理器
        if (messageProcessingHandler == null) {
            // 获取自定消息处理器，如果自定义处理器则使用默认处理器。
            String handler = ConfKit.get("MessageProcessingHandlerImpl");
            handler = handler == null ? DEFAULT_HANDLER : handler;
            try {
                Class<?> clazz = Thread.currentThread().getContextClassLoader().loadClass(handler);
                messageProcessingHandler = (MessageProcessingHandler) clazz.newInstance();
            } catch (Exception e) {
                throw new RuntimeException("messageProcessingHandler Load Error！");
            }
        }
        String xml = "";
        try {
            //取得消息类型
            String type = inMessage.getMsgType();
            Method alMt = messageProcessingHandler.getClass().getMethod("allType", InMessage.class);
            oms = (OutMessage) alMt.invoke(messageProcessingHandler, inMessage);
            
            Method mt = messageProcessingHandler.getClass().getMethod(type + "TypeMsg", InMessage.class);
            if(mt != null){
            	OutMessage mtMms = (OutMessage) mt.invoke(messageProcessingHandler, inMessage);
            	if(mtMms!=null){
            		oms = mtMms;
            	}
            }
            setMsgInfo(oms, inMessage);
        } catch (Exception e) {
            
        }
        if(oms != null){
            // 把发送发送对象转换为xml输出
            XStream xs = XStreamFactory.init(true);
            xs.ignoreUnknownElements();
            xs.alias("xml", oms.getClass());
            xs.alias("item", Articles.class);
            xml = xs.toXML(oms);
        }
        return xml;
    }

    /**
     * 获取用户信息
     * @param openid
     * @return
     * @throws IOException 
     * @throws NoSuchProviderException 
     * @throws NoSuchAlgorithmException 
     * @throws KeyManagementException 
     */
    @SuppressWarnings("unchecked")
	public static Map<String, Object> getInfo(String openid,String accessToken) throws KeyManagementException, NoSuchAlgorithmException, NoSuchProviderException, IOException {
        String url = USER_INFO_URL + accessToken + "&openid=" + openid;
        String jsonStr = HttpKit.get(url);
        return JSON.parseObject(jsonStr,Map.class);
    }

    /**
     * 设置发送消息体
     * @param oms
     * @param msg
     * @throws Exception
     */
    private static void setMsgInfo(OutMessage oms,InMessage msg) throws Exception {
    	if(oms != null){
    		Class<?> outMsg = oms.getClass().getSuperclass();
            Field CreateTime = outMsg.getDeclaredField("CreateTime");
            Field ToUserName = outMsg.getDeclaredField("ToUserName");
            Field FromUserName = outMsg.getDeclaredField("FromUserName");

            ToUserName.setAccessible(true);
            CreateTime.setAccessible(true);
            FromUserName.setAccessible(true);

            CreateTime.set(oms, new Date().getTime());
            ToUserName.set(oms, msg.getFromUserName());
            FromUserName.set(oms, msg.getToUserName());
    	}
    }

    /**
     *消息体转换
     * @param responseInputString
     * @return
     */
    private static InMessage parsingInMessage(String responseInputString) {
        //转换微信post过来的xml内容
        XStream xs = XStreamFactory.init(false);
        xs.ignoreUnknownElements();
        xs.alias("xml", InMessage.class);
        InMessage msg = (InMessage) xs.fromXML(responseInputString);
        return msg;
    }
    
    /**
     * 获取媒体资源
     * @param accessToken
     * @param mediaId
     * @return
     * @throws IOException
     */
    public static Attachment getMedia(String accessToken,String mediaId) throws IOException{
    	String url = GET_MEDIA_URL + accessToken + "&media_id=" + mediaId;
        return HttpKit.download(url);
    }
    
    /**
     * 上传素材文件
     * @param type
     * @param file
     * @return
     * @throws KeyManagementException
     * @throws NoSuchAlgorithmException
     * @throws NoSuchProviderException
     * @throws IOException
     */
    @SuppressWarnings("unchecked")
	public static Map<String, Object> uploadMedia(String accessToken,String type,File file) throws KeyManagementException, NoSuchAlgorithmException, NoSuchProviderException, IOException {
        String url = UPLOAD_MEDIA_URL + accessToken +"&type="+type;
        String jsonStr = HttpKit.upload(url,file);
        return JSON.parseObject(jsonStr, Map.class);
    }
}