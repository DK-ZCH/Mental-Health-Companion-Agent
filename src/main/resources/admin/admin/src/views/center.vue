<template>
  <div>
    <el-form
      class="detail-form-content"
      ref="ruleForm"
      :model="ruleForm"
      label-width="80px"
    >  
     <el-row>
                    <el-col :span="12">
           <el-form-item v-if="flag=='yonghu'"  label='学生姓名' prop="name">
               <el-input v-model="ruleForm.name"  placeholder='学生姓名' clearable></el-input>
           </el-form-item>
         </el-col>

         <el-col :span="12">
           <el-form-item v-if="flag=='yonghu'"  label='学生手机号' prop="phone">
               <el-input v-model="ruleForm.phone"  placeholder='学生手机号' clearable></el-input>
           </el-form-item>
         </el-col>

         <el-col :span="12">
           <el-form-item v-if="flag=='yonghu'"  label='学生身份证号' prop="idCardNo">
               <el-input v-model="ruleForm.idCardNo"  placeholder='学生身份证号' clearable></el-input>
           </el-form-item>
         </el-col>

         <el-col :span="12">
             <el-form-item v-if="flag=='yonghu'" label='学生头像' prop="avatarUrl">
                 <file-upload
                         tip="点击上传照片"
                         action="file/upload"
                         :limit="3"
                         :multiple="true"
                         :fileUrls="ruleForm.avatarUrl?ruleForm.avatarUrl:''"
                         @change="yonghuPhotoUploadChange"
                 ></file-upload>
             </el-form-item>
         </el-col>
         <el-col :span="12">
           <el-form-item v-if="flag=='yonghu'"  label='电子邮箱' prop="email">
               <el-input v-model="ruleForm.email"  placeholder='电子邮箱' clearable></el-input>
           </el-form-item>
         </el-col>

         <el-col :span="12">
           <el-form-item v-if="flag=='xinlilaoshi'"  label='心理老师姓名' prop="name">
               <el-input v-model="ruleForm.name"  placeholder='心理老师姓名' clearable></el-input>
           </el-form-item>
         </el-col>

         <el-col :span="12">
           <el-form-item v-if="flag=='xinlilaoshi'"  label='心理老师手机号' prop="phone">
               <el-input v-model="ruleForm.phone"  placeholder='心理老师手机号' clearable></el-input>
           </el-form-item>
         </el-col>

         <el-col :span="12">
             <el-form-item v-if="flag=='xinlilaoshi'" label='心理老师头像' prop="avatarUrl">
                 <file-upload
                         tip="点击上传照片"
                         action="file/upload"
                         :limit="3"
                         :multiple="true"
                         :fileUrls="ruleForm.avatarUrl?ruleForm.avatarUrl:''"
                         @change="xinlilaoshiPhotoUploadChange"
                 ></file-upload>
             </el-form-item>
         </el-col>
         <el-col :span="12">
           <el-form-item v-if="flag=='xinlilaoshi'"  label='电子邮箱' prop="email">
               <el-input v-model="ruleForm.email"  placeholder='电子邮箱' clearable></el-input>
           </el-form-item>
         </el-col>

         <el-col :span="12">
           <el-form-item v-if="flag=='xinlilaoshi'"  label='擅长' prop="expertise">
               <el-input v-model="ruleForm.expertise"  placeholder='擅长' clearable></el-input>
           </el-form-item>
         </el-col>

         <el-col :span="12">
           <el-form-item v-if="flag=='xinlilaoshi'"  label='履历' prop="resume">
               <el-input v-model="ruleForm.resume"  placeholder='履历' clearable></el-input>
           </el-form-item>
         </el-col>

         <el-col :span="24">
             <el-form-item v-if="flag=='xinlilaoshi'"  label="心理老师详细介绍" prop="introduction">
                 <editor
                         style="min-width: 200px; max-width: 600px;"
                         v-model="ruleForm.introduction"
                         class="editor"
                         action="file/upload">
                 </editor>
             </el-form-item>
         </el-col>
         <el-form-item v-if="flag=='users'" label="学生名" prop="username">
             <el-input v-model="ruleForm.username"
                       placeholder="学生名"></el-input>
         </el-form-item>
         <el-col :span="12">
             <el-form-item v-if="flag!='users'"  label="性别" prop="gender">
                 <el-select v-model="ruleForm.gender" placeholder="请选择性别">
                     <el-option
                             v-for="(item,index) in sexTypesOptions"
                             v-bind:key="item.itemCode"
                             :label="item.itemName"
                             :value="item.itemCode">
                     </el-option>
                 </el-select>
             </el-form-item>
         </el-col>
         <el-col :span="24">
             <el-form-item>
                 <el-button type="primary" @click="onUpdateHandler">修 改</el-button>
             </el-form-item>
         </el-col>
     </el-row>
    </el-form>
  </div>
</template>
<script>
// 数字，邮件，手机，url，身份证校验
import { isNumber,isIntNumer,isEmail,isMobile,isPhone,isURL,checkIdCard } from "@/utils/validate";

export default {
  data() {
    return {
        ruleForm: {},
        flag: '',
        usersFlag: false,
        // sexTypesOptions : [],
// 注册表 学生
    // 注册的类型字段 学生
        // 性别
        sexTypesOptions : [],
// 注册表 心理老师
    // 注册的类型字段 心理老师
        // 性别
        sexTypesOptions : [],
    };
  },
  mounted() {
    //获取当前登录学生的信息
    var table = this.$storage.get("sessionTable");
    this.sessionTable = this.$storage.get("sessionTable");
    this.role = this.$storage.get("role");
    if (this.role != "管理员"){
    }

    this.flag = table;
    this.$http({
      url: `${this.$storage.get("sessionTable")}/session`,
      method: "get"
    }).then(({ data }) => {
      if (data && data.code === 0) {
        this.ruleForm = data.data;
// 注册表 学生
// 注册表 心理老师
      } else {
        this.$message.error(data.msg);
      }
    });

// 注册表 学生 的级联表
// 注册表 心理老师 的级联表

      this.$http({
          url: `dictionary/page?page=1&limit=100&sort=&order=&dictCode=sex_types`,
          method: "get"
      }).then(({ data }) => {
          if (data && data.code === 0) {
              this.sexTypesOptions = data.data.list;
          } else {
              this.$message.error(data.msg);
          }
      });
  },
  methods: {
    yonghuPhotoUploadChange(fileUrls) {
        this.ruleForm.avatarUrl = fileUrls;
    },
    xinlilaoshiPhotoUploadChange(fileUrls) {
        this.ruleForm.avatarUrl = fileUrls;
    },


    onUpdateHandler() {
                         if((!this.ruleForm.name)&& 'yonghu'==this.flag){
                             this.$message.error('学生姓名不能为空');
                             return
                         }

                             if( 'yonghu' ==this.flag && this.ruleForm.phone&&(!isMobile(this.ruleForm.phone))){
                                 this.$message.error(`手机应输入手机格式`);
                                 return
                             }
                         if((!this.ruleForm.idCardNo)&& 'yonghu'==this.flag){
                             this.$message.error('学生身份证号不能为空');
                             return
                         }

                         if((!this.ruleForm.avatarUrl)&& 'yonghu'==this.flag){
                             this.$message.error('学生头像不能为空');
                             return
                         }

                             if( 'yonghu' ==this.flag && this.ruleForm.email&&(!isEmail(this.ruleForm.email))){
                                 this.$message.error(`邮箱应输入邮箱格式`);
                                 return
                             }
                         if((!this.ruleForm.name)&& 'xinlilaoshi'==this.flag){
                             this.$message.error('心理老师姓名不能为空');
                             return
                         }

                             if( 'xinlilaoshi' ==this.flag && this.ruleForm.phone&&(!isMobile(this.ruleForm.phone))){
                                 this.$message.error(`手机应输入手机格式`);
                                 return
                             }
                         if((!this.ruleForm.avatarUrl)&& 'xinlilaoshi'==this.flag){
                             this.$message.error('心理老师头像不能为空');
                             return
                         }

                             if( 'xinlilaoshi' ==this.flag && this.ruleForm.email&&(!isEmail(this.ruleForm.email))){
                                 this.$message.error(`邮箱应输入邮箱格式`);
                                 return
                             }
                         if((!this.ruleForm.expertise)&& 'xinlilaoshi'==this.flag){
                             this.$message.error('擅长不能为空');
                             return
                         }

                         if((!this.ruleForm.resume)&& 'xinlilaoshi'==this.flag){
                             this.$message.error('履历不能为空');
                             return
                         }

                         if((!this.ruleForm.introduction)&& 'xinlilaoshi'==this.flag){
                             this.$message.error('心理老师详细介绍不能为空');
                             return
                         }

        if((!this.ruleForm.gender) && this.flag!='users'){
            this.$message.error('性别不能为空');
            return
        }
      if('users'==this.flag && this.ruleForm.username.trim().length<1) {
        this.$message.error(`学生名不能为空`);
        return	
      }
      this.$http({
        url: `${this.$storage.get("sessionTable")}/update`,
        method: "post",
        data: this.ruleForm
      }).then(({ data }) => {
        if (data && data.code === 0) {
          this.$message({
            message: "修改信息成功",
            type: "success",
            duration: 1500,
            onClose: () => {
            }
          });
        } else {
          this.$message.error(data.msg);
        }
      });
    }
  }
};
</script>
<style lang="scss" scoped>
</style>
