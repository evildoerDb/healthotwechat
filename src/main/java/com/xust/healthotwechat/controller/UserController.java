package com.xust.healthotwechat.controller;

import com.google.gson.Gson;
import com.xust.healthotwechat.VO.AjaxResultVo;
import com.xust.healthotwechat.VO.ResultVO;
import com.xust.healthotwechat.entity.User;
import com.xust.healthotwechat.exception.HealthOTWechatErrorCode;
import com.xust.healthotwechat.exception.HealthOTWechatException;
import com.xust.healthotwechat.facade.UserFacadeService;
import com.xust.healthotwechat.form.ForgetForm;
import com.xust.healthotwechat.form.UserForm;
import com.xust.healthotwechat.form.UserLoginForm;
import com.xust.healthotwechat.utils.AjaxResultVOUtils;
import com.xust.healthotwechat.utils.EncryptUtils;
import com.xust.healthotwechat.utils.ResultVOUtils;
import com.xust.healthotwechat.utils.SmsUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import java.util.concurrent.TimeUnit;


/**
 * Created by evildoerdb_ on 2018/5/6
 *
 * 用户controller
 */

@RestController
@Slf4j
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserFacadeService userFacadeService;

    @Autowired
    private RedisTemplate redisTemplate;



    @PostMapping("/login")
    public String login(@Valid UserLoginForm userLoginForm,
                        BindingResult bindingResult, HttpServletRequest request){

        Gson gson = new Gson();

        AjaxResultVo ajaxResultVo;

        HttpSession session = request.getSession();

        try {

            if (bindingResult.hasErrors()){
                throw  new RuntimeException(bindingResult.getFieldError().getDefaultMessage());
            }

            /**密码验证*/
            User user = userFacadeService.findUserByPhone(userLoginForm.getPhone());
            String password = EncryptUtils.encrypt(userLoginForm.getPassword());
            if(null == user || !user.getPassword().equals(password)){
                throw new HealthOTWechatException(HealthOTWechatErrorCode.USER_ERROE.getCode(),
                        HealthOTWechatErrorCode.USER_ERROE.getMessage());
            }

            //加入session
            session.setAttribute("user",userLoginForm.getPhone());

            ajaxResultVo = AjaxResultVOUtils.success("index.html","登录成功");

        }catch (Exception e){
            log.error("【登录异常】={}",userLoginForm.getPhone()+e.getMessage());
            ajaxResultVo = AjaxResultVOUtils.error("login.html",e.getMessage());
        }

        return gson.toJson(ajaxResultVo);

    }



    /**
     * 注册
     * @param userForm  用户表单提交对象
     * @param bindingResult 参数校验结果对象
     * @return  返回登录页面
     */
    @RequestMapping("/register")
    public String register(@Valid UserForm userForm,
                                 BindingResult bindingResult){

        Gson gson = new Gson();
        AjaxResultVo resultVo;


        try{
            /**用户校验出错*/
            if (bindingResult.hasErrors()){
                throw new RuntimeException(bindingResult.getFieldError().getDefaultMessage());
            }

            /**校验验证码是否正确*/
            if (!checkValidateCode(userForm.getValidateCode(),userForm.getPhone())){
                throw new HealthOTWechatException(HealthOTWechatErrorCode.VALIDATE_CODE_ERROE.getCode(),
                        HealthOTWechatErrorCode.VALIDATE_CODE_ERROE.getMessage());
            }

            /**查询用户是否存在*/
            boolean isExist = userFacadeService.userIsExist(userForm.getPhone());

            /**用户已经存在*/
            if (isExist){
                throw new HealthOTWechatException(HealthOTWechatErrorCode.USER_ISEXIST_ERROE.getCode(),
                        HealthOTWechatErrorCode.USER_ISEXIST_ERROE.getMessage());
            }

            /**进行注册*/
            userFacadeService.register(userForm);

            resultVo = AjaxResultVOUtils.success("login.html","成功");



        }catch (Exception e){
            log.error("【注册异常】={}",e.getMessage());
            resultVo = AjaxResultVOUtils.error("register.html",e.getMessage());
        }
        return gson.toJson(resultVo);
    }


    /**
     * 生成验证码
     * @param phone 用户手机号码
     * @return
     */
    @GetMapping("/generateValidateCode")
    public String generateValidateCode(@RequestParam("phone") String phone){
        Gson gson = new Gson();

        try {



            /**生成六位随机验证码*/
            String validateCode = SmsUtils.verificationCode();

            //调用短信平台发送验证码
            SmsUtils.sendSms(phone,validateCode);

            /**redis中加入验证码 过期时间为五分钟分钟*/
            redisTemplate.opsForValue().set(phone+"_validateCode",validateCode,300,TimeUnit.SECONDS);

        }catch (Exception e){
            log.error("【发送验证码失败】={}",e.getMessage());
           return gson.toJson(ResultVOUtils.error(233,"发送验证码失败，请重新发送"));
        }

        return gson.toJson(ResultVOUtils.success(null,"验证码发送成功"));

    }


    /**
     * 校验验证码是否正确
     * @param code
     * @param phone
     * @return
     */
    private boolean checkValidateCode(String code,String phone){

        /**从redis中获取验证码*/
        String value = (String) redisTemplate.opsForValue().get(phone+"_validateCode");

        /**如果验证码为空或者验证码错误*/
        if (null == value || !value.equals(code)){
            return false;
        }

        return true;
    }


    /***
     * 根据手机号码修改密码
     * @param phone
     * @param oldPassword
     * @param newPassword
     * @return
     */
    @PostMapping("/updatepassword")
    @ResponseBody
    public String changePassword(@RequestParam("newPassword") String newPassword,
                                 @RequestParam("oldPassword") String oldPassword,
                                 HttpServletRequest request){

        Gson gson = new Gson();

        AjaxResultVo ajaxResultVo;

        String phone = (String) request.getSession().getAttribute("user");

        try {
            /**先去查询密码是否正确*/
            oldPassword = EncryptUtils.encrypt(oldPassword);

            User user = userFacadeService.findUserByPhone(phone);
            if (null == user || !oldPassword.equals(user.getPassword())){
                throw new HealthOTWechatException(HealthOTWechatErrorCode.USER_ERROE.getCode(),
                        HealthOTWechatErrorCode.USER_ERROE.getMessage());
            }

            /** 先对密码进行加密 再去数据库修改密码*/
            newPassword = EncryptUtils.encrypt(newPassword);
            userFacadeService.updatePassword(phone,newPassword);

            ajaxResultVo = AjaxResultVOUtils.success("login.html","密码修改成功");
            request.getSession().removeAttribute("user");

        }catch (RuntimeException e){
            log.error("【修改密码异常】={}",e.getMessage());
            ajaxResultVo = AjaxResultVOUtils.error("index.html",e.getMessage());

        }

        return gson.toJson(ajaxResultVo);
    }


    /**
     * 注销当前账号
     * @param request
     * @return
     */
    @RequestMapping("/logout")
    public ResultVO<String> logout(HttpServletRequest request){
        try{
            request.getSession().removeAttribute("user");
        }catch (Exception e){
           return AjaxResultVOUtils.error();

        }
        return AjaxResultVOUtils.success("注销成功");
    }


    /**
     * 判断是否已经登录
     * @param request
     * @return
     */
    @RequestMapping(value = "/isLogin",method = RequestMethod.GET)
    public ResultVO<String> isLogin(HttpServletRequest request){

        String phone = (String) request.getSession().getAttribute("user");
        if (phone == null){
            return  ResultVOUtils.error(233,"请登录");
        }
        return ResultVOUtils.success(666,phone,"成功");
    }



    @PostMapping("/forgetpassword")
    @ResponseBody
    public String forget(@Valid ForgetForm forgetForm, BindingResult bindingResult){
        Gson gson = new Gson();
        AjaxResultVo ajaxResultVo;
        try{

            /**用户校验出错*/
            if (bindingResult.hasErrors()){
                throw new RuntimeException(bindingResult.getFieldError().getDefaultMessage());
            }

            /**校验验证码是否正确*/
            if (!checkValidateCode(forgetForm.getValidateCode(),forgetForm.getPhone())){
                throw new HealthOTWechatException(HealthOTWechatErrorCode.VALIDATE_CODE_ERROE.getCode(),
                        HealthOTWechatErrorCode.VALIDATE_CODE_ERROE.getMessage());
            }
            /**查询用户是否存在*/
            boolean isExist = userFacadeService.userIsExist(forgetForm.getPhone());

            /**用户已经存在*/
            if (!isExist){
                throw new HealthOTWechatException(HealthOTWechatErrorCode.USER_ALREADY_ISEXIST_ERROE.getCode(),
                        HealthOTWechatErrorCode.USER_ALREADY_ISEXIST_ERROE.getMessage());
            }
            userFacadeService.updatePassword(forgetForm.getPhone(),EncryptUtils.encrypt(forgetForm.getPassword()));
            ajaxResultVo = AjaxResultVOUtils.success("login.html","密码重置成功");
        }catch (Exception e){
            log.error("【注册异常】={}",e.getMessage());
            ajaxResultVo = AjaxResultVOUtils.error("register.html",e.getMessage());
        }

        return gson.toJson(ajaxResultVo);
    }
}
