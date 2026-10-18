<template>
    <div class="addEdit-block">
        <el-form
                class="detail-form-content"
                ref="ruleForm"
                :model="ruleForm"
                :rules="rules"
                label-width="80px"
                :style="{backgroundColor:addEditForm.addEditBoxColor}"
        >
            <el-row>

                <el-col :span="12">
                    <el-form-item class="select" v-if="type!='info'"  label="试卷表" prop="paperId">
                        <el-select v-model="ruleForm.paperId" placeholder="请选择试卷表" @change="exampaperChange">
                            <el-option
                                    v-for="(item,index) in exampaperOptions"
                                    v-bind:key="item.id"
                                    :label="item.paperName"
                                    :value="item.id">
                            </el-option>
                        </el-select>
                    </el-form-item>
                </el-col>
                <input id="updateId" name="id" type="hidden">
                <input id="paperId" name="paperId" type="hidden">
                <el-col :span="24">
                    <el-form-item class="input" style="width: 580px;" v-if="type!='info'"  label="试题名称" prop="content">
                        <el-input
                                type="textarea"
                                min="1"
                                :readonly="ro.content"
                                v-model="ruleForm.content"
                                placeholder="试题名称"
                                clearable
                        ></el-input>
                    </el-form-item>
                    <div v-else>
                        <el-form-item class="input" style="width: 580px;" label="试题名称" prop="content">
                            <el-input
                                    type="textarea"
                                    min="1"
                                    v-model="ruleForm.content"
                                    placeholder="试题名称"
                                    clearable
                                    readonly
                            ></el-input>
                        </el-form-item>
                    </div>
                </el-col>
                <el-col :span="24">
                    <el-form-item class="select" v-if="type!='info'"  label="试题类型" prop="questionType">
                        <el-select @change="typeChange" v-model="ruleForm.questionType" :readonly="ro.questionType" placeholder="请选择试题类型">
                            <el-option
                                    v-for="(item,index) in examquestionTypesOptions"
                                    v-bind:key="item.itemCode"
                                    :label="item.itemName"
                                    :value="item.itemCode">
                            </el-option>
                        </el-select>
                    </el-form-item>
                    <div v-else>
                        <el-form-item class="input" label="试题类型" prop="questionTypeLabel">
                            <el-input v-model="ruleForm.questionTypeLabel"
                                      placeholder="试题类型" readonly></el-input>
                        </el-form-item>
                    </div>
                </el-col>
                <el-col :span="24"  v-if="ruleForm.questionType==1||ruleForm.questionType==2">
                    <el-form-item v-if="type!='info'" label="选项" prop="options">
                        <div class="options" v-for="(item,index) in options" v-bind:key="index">
                            <span>{{item.code}}.</span>{{item.text}}
                            <el-button v-if="ruleForm.questionType==1||ruleForm.questionType==2" size="mini" @click="deleteOptions(index)" type="warning" round>删除</el-button>
                        </div>
                        <el-button size="small" v-if="(ruleForm.questionType==1||ruleForm.questionType==2)&&this.options.length<4" @click="addOptionsDialog" type="primary" round>添加选项</el-button>
                    </el-form-item>
                    <el-form-item  v-else label="选项" prop="options">
                        <div class="options" v-for="(item,index) in options" v-bind:key="index">
                            <span>{{item.code}}.</span>{{item.text}}
                        </div>
                    </el-form-item>
                </el-col>
                <el-col :span="24">
                    <div v-if="type!='info'">
                        <el-form-item v-if="ruleForm.questionType==1 && options.length>0" label="正确答案" prop="answer">
                            <el-select v-model="ruleForm.answer" :readonly="ro.answer">
                                <el-option
                                        :label="item.code"
                                        :value="item.code"
                                        v-for="(item,index) in options"
                                        v-bind:key="index"
                                ></el-option>
                            </el-select>
                        </el-form-item>
                        <el-form-item v-if="ruleForm.questionType==2 && options.length>0" label="正确答案" prop="answer">
                            <el-select v-model="ruleForm.answer" :readonly="ro.answer" multiple>
                                <el-option
                                        :label="item.code"
                                        :value="item.code"
                                        v-for="(item,index) in options"
                                        v-bind:key="index"
                                ></el-option>
                            </el-select>
                        </el-form-item>
                        <el-form-item v-if="ruleForm.questionType==3 && options.length>0" label="正确答案" prop="answer">
                            <el-select v-model="ruleForm.answer" :readonly="ro.answer">
                                <el-option
                                        :label="item.text"
                                        :value="item.code"
                                        v-for="(item,index) in options"
                                        v-bind:key="index"
                                ></el-option>
                            </el-select>
                        </el-form-item>
                        <el-form-item v-if="ruleForm.questionType==4" label="正确答案" prop="answer">
                            <el-input v-model="ruleForm.answer" :readonly="ro.answer" placeholder="正确答案" clearable></el-input>
                        </el-form-item>
                    </div>
                    <div v-else>
                        <el-form-item class="input" label="正确答案" prop="answer">
                            <el-input v-model="ruleForm.answer"
                                      placeholder="正确答案" readonly></el-input>
                        </el-form-item>
                    </div>
                </el-col>
                <el-col :span="24">
                    <el-form-item class="input" v-if="type!='info'"  label="分值" prop="score">
                        <el-input style="width: 580px;"  v-model="ruleForm.score"
                                  placeholder="分值" clearable  :readonly="ro.score"></el-input>
                    </el-form-item>
                    <div v-else>
                        <el-form-item class="input" label="分值" prop="score">
                            <el-input style="width: 580px;"  v-model="ruleForm.score"
                                      placeholder="分值" readonly></el-input>
                        </el-form-item>
                    </div>
                </el-col>
                <el-col :span="24">
                    <el-form-item class="input" v-if="type!='info'"  label="答案解析" prop="analysis">
                        <el-input
                                style="width: 580px;"
                                type="textarea"
                                min="1"
                                :readonly="ro.analysis"
                                v-model="ruleForm.analysis"
                                placeholder="答案解析"
                                clearable
                        ></el-input>

                    </el-form-item>
                    <div v-else>
                        <el-form-item class="input" label="答案解析" prop="analysis">
                            <el-input
                                    style="width: 580px;"
                                    type="textarea"
                                    min="1"
                                    v-model="ruleForm.analysis"
                                    placeholder="答案解析"
                                    clearable
                                    readonly
                            ></el-input>
                        </el-form-item>
                    </div>
                </el-col>
                <el-col :span="12">
                    <el-form-item class="input" v-if="type!='info'"  label="试题排序" prop="sortOrder">
                        <el-input v-model="ruleForm.sortOrder"
                                  placeholder="试题排序" clearable  :readonly="ro.sortOrder"></el-input>
                    </el-form-item>
                    <div v-else>
                        <el-form-item class="input" label="试题排序" prop="sortOrder">
                            <el-input v-model="ruleForm.sortOrder"
                                      placeholder="试题排序，值越大排越前面" readonly></el-input>
                        </el-form-item>
                    </div>
                </el-col>


            </el-row>
            <el-form-item class="btn">
                <el-button v-if="type!='info'" type="primary" class="btn-success" @click="onSubmit">提交</el-button>
                <el-button v-if="type!='info'" class="btn-close" @click="back()">取消</el-button>
                <el-button v-if="type=='info'" class="btn-close" @click="back()">返回</el-button>
            </el-form-item>
        </el-form>

        <!-- 选项单选多选添加选项 -->
        <el-dialog title="添加选项" :visible.sync="addOptionsDialogVisiable" width="50%">
            <el-form ref="dialogForm" :model="dialogForm" :rules="dialogRules" label-width="80px">
                <el-form-item label="选项" prop="code">
                    <el-select v-model="dialogForm.code" placeholder="选项">
                        <el-option label="A" value="A"></el-option>
                        <el-option label="B" value="B"></el-option>
                        <el-option label="C" value="C"></el-option>
                        <el-option label="D" value="D"></el-option>
                    </el-select>
                </el-form-item>
                <el-form-item label="内容" prop="text">
                    <el-input type="textarea" min="1" v-model="dialogForm.text" placeholder="内容" clearable></el-input>
                </el-form-item>
            </el-form>
            <span slot="footer" class="dialog-footer">
        <el-button @click="addOptionsDialogVisiable = false">取 消</el-button>
        <el-button type="primary" @click="addOptions">确 定</el-button>
      </span>
        </el-dialog>

    </div>
</template>
<script>
    import styleJs from "../../../utils/style.js";
    export default {
        data() {
            return {
                addEditForm:null,
                id: '',
                //选项
                options: [],
                //选项 添加选项、删除选项 弹出框
                addOptionsDialogVisiable: false,
                //弹出框内输入的数据
                dialogForm: {},
                //弹出框验证输入信息
                dialogRules: {
                    text: [{ required: true, message: "请填写内容", trigger: "blur" }],
                    code: [{ required: true, message: "请选择选项", trigger: "blur" }]
                },

                type: '',

                exampaperForm: {},
                ro:{
                    paperId: false,
                    content: false,
                    options: false,
                    score: false,
                    answer: false,
                    analysis: false,
                    questionType: false,
                    sortOrder: false,
                },
                ruleForm: {
                    paperId: '',
                    content: '',
                    options: '',
                    score: '',
                    answer: '',
                    analysis: '',
                    questionType: '',
                    sortOrder: '',
                },
                examquestionTypesOptions : [],
                exampaperOptions : [],
                rules: {
                    paperId: [
                        { required: true, message: '所属试卷id（外键）不能为空', trigger: 'blur' },
                    ],
                    content: [
                        { required: true, message: '试题名称不能为空', trigger: 'blur' },
                    ],
                    options: [
                        { required: true, message: '选项，json字符串不能为空', trigger: 'blur' },
                    ],
                    score: [
                        { required: true, message: '分值不能为空', trigger: 'blur' },
                    ],
                    answer: [
                        { required: true, message: '正确答案不能为空', trigger: 'blur' },
                    ],
                    analysis: [
                        { required: true, message: '答案解析不能为空', trigger: 'blur' },
                    ],
                    questionType: [
                        { required: true, message: '试题类型不能为空', trigger: 'blur' },
                    ],
                    sortOrder: [
                        { required: true, message: '试题排序，值越大排越前面不能为空', trigger: 'blur' },
                    ],
                }
            };
        },
        props: ["parent"],
        computed: {
        },
        created() {
            this.addEditForm = styleJs.addStyle();
            this.addEditStyleChange()
            this.addEditUploadStyleChange()
            //获取下拉框信息
            this.$http({
                url:`dictionary/page?page=1&limit=100&sort=&order=&dictCode=examquestion_types`,
                method: "get"
            }).then(({ data }) => {
                if (data && data.code === 0) {
                    this.examquestionTypesOptions = data.data.list;
                }
            });

            this.$http({
                url: `assessmentPaper/page?page=1&limit=100`,
                method: "get"
            }).then(({ data }) => {
                if (data && data.code === 0) {
                    this.exampaperOptions = data.data.list;
                }
            });

        },
        methods: {
            //选项 弹出框确定触发提交事件
            addOptions() {
                this.$refs["dialogForm"].validate(valid => {
                    if (valid) {
                        for (let i = 0; i < this.options.length; i++) {
                            if(this.options[i].code==this.dialogForm.code){
                                this.$message({
                                    message: '已经有 '+this.dialogForm.code+' 这个选项了',
                                    type: 'warning'
                                });
                                this.dialogForm = {};
                                return false;
                            }
                        }
                        this.options.push({
                            text: this.dialogForm.text,
                            code: this.dialogForm.code
                        });
                        this.dialogForm = {};
                        this.addOptionsDialogVisiable = !this.addOptionsDialogVisiable;
                    }
                });
            },
            //选项 打开添加选项弹出框
            addOptionsDialog() {
                this.addOptionsDialogVisiable = !this.addOptionsDialogVisiable;
            },
            //选项 单选多选删除选项
            deleteOptions(index) {
                this.options.splice(index, 1);
            },
            //选项 判断题选项添加
            typeChange(e) {
                if (e == 3) {
                    this.options = [];
                    this.options.push({
                        text: "A.对",
                        code: "A"
                    });
                    this.options.push({
                        text: "B.错",
                        code: "B"
                    });
                } else {
                    this.options = [];
                }
            },
            // 下载
            download(file){

                window.open(`${file}`)
            },
            // 初始化
            init(id,type) {
                if (id) {
                    this.id = id;
                    this.type = type;
                }
                if(this.type=='info'||this.type=='else'){
                    this.info(id);
                }else if(this.type=='cross'){
                    var obj = this.$storage.getObj('crossObj');
                    for (var o in obj){

                        if(o=='paperId'){
                            this.ruleForm.paperId = obj[o];
                            this.ro.paperId = true;
                            continue;
                        }
                        if(o=='content'){
                            this.ruleForm.content = obj[o];
                            this.ro.content = true;
                            continue;
                        }
                        if(o=='options'){
                            this.ruleForm.options = obj[o];
                            this.ro.options = true;
                            continue;
                        }
                        if(o=='score'){
                            this.ruleForm.score = obj[o];
                            this.ro.score = true;
                            continue;
                        }
                        if(o=='answer'){
                            this.ruleForm.answer = obj[o];
                            this.ro.answer = true;
                            continue;
                        }
                        if(o=='analysis'){
                            this.ruleForm.analysis = obj[o];
                            this.ro.analysis = true;
                            continue;
                        }
                        if(o=='questionType'){
                            this.ruleForm.questionType = obj[o];
                            this.ro.questionType = true;
                            continue;
                        }
                        if(o=='sortOrder'){
                            this.ruleForm.sortOrder = obj[o];
                            this.ro.sortOrder = true;
                            continue;
                        }
                    }
                }
                // 获取学生信息
                this.$http({
                    url:`${this.$storage.get("sessionTable")}/session`,
                    method: "get"
                }).then(({ data }) => {
                    if (data && data.code === 0) {
                        var json = data.data;
                    } else {
                        this.$message.error(data.msg);
                    }
                });


            },
            exampaperChange(id){
                this.$http({
                    url: `assessmentPaper/info/`+id,
                    method: "get"
                }).then(({ data }) => {
                    if (data && data.code === 0) {
                        this.exampaperForm = data.data;
                    }
                });
            },
            // 多级联动参数
            info(id) {
                this.$http({
                    url: `assessmentQuestion/info/${id}`,
                    method: 'get'
                }).then(({ data }) => {
                    if (data && data.code === 0) {
                        this.ruleForm = data.data;
                        this.exampaperChange(data.data.paperId)
                        this.options = JSON.parse(this.ruleForm.options);
                        if (this.ruleForm.questionType == 2) {
                            this.ruleForm.answer = this.ruleForm.answer.split(",");
                        }
                        this.options = JSON.parse(this.ruleForm.options)
                        //解决前台上传图片后台不显示的问题
                        let reg=new RegExp('../../../upload','g')//g代表全部
                    } else {
                        this.$message.error(data.msg);
                    }
                });
            },
            // 提交
            onSubmit() {
                if (!this.options && this.questionType != 4) {
                    this.$message.error("请设置选项");
                    return;
                }
                if (this.ruleForm.questionType == 2) {
                    this.ruleForm.answer = this.ruleForm.answer.join(",");
                }
                this.ruleForm.options = JSON.stringify(this.options);
                this.$refs["ruleForm"].validate(valid => {
                    if (valid) {
                        this.$http({
                            url:`assessmentQuestion/${!this.ruleForm.id ? "saveExamquestion" : "updateExamquestion"}`,
                            method: "post",
                            data: this.ruleForm
                        }).then(({ data }) => {
                            if (data && data.code === 0) {
                                this.$message({
                                    message: "操作成功",
                                    type: "success",
                                    duration: 1500,
                                    onClose: () => {
                                        this.parent.showFlag = true;
                                        this.parent.addOrUpdateFlag = false;
                                        this.parent.examquestionCrossAddOrUpdateFlag = false;
                                        this.parent.search();
                                        this.parent.contentStyleChange();
                                    }
                                });
                            } else {
                                this.$message.error(data.msg);
                            }
                        });
                    }
                });
            },
            // 获取uuid
            getUUID () {
                return new Date().getTime();
            },
            // 返回
            back() {
                this.parent.showFlag = true;
                this.parent.addOrUpdateFlag = false;
                this.parent.examquestionCrossAddOrUpdateFlag = false;
                this.parent.contentStyleChange();
            },
            //图片

            addEditStyleChange() {
                this.$nextTick(()=>{
                    // input
                    document.querySelectorAll('.addEdit-block .input .el-input__inner').forEach(el=>{
                        el.style.height = this.addEditForm.inputHeight
                        el.style.color = this.addEditForm.inputFontColor
                        el.style.fontSize = this.addEditForm.inputFontSize
                        el.style.borderWidth = this.addEditForm.inputBorderWidth
                        el.style.borderStyle = this.addEditForm.inputBorderStyle
                        el.style.borderColor = this.addEditForm.inputBorderColor
                        el.style.borderRadius = this.addEditForm.inputBorderRadius
                        el.style.backgroundColor = this.addEditForm.inputBgColor
                    })
                    document.querySelectorAll('.addEdit-block .input .el-form-item__label').forEach(el=>{
                        el.style.lineHeight = this.addEditForm.inputHeight
                        el.style.color = this.addEditForm.inputLableColor
                        el.style.fontSize = this.addEditForm.inputLableFontSize
                    })
                    // select
                    document.querySelectorAll('.addEdit-block .select .el-input__inner').forEach(el=>{
                        el.style.height = this.addEditForm.selectHeight
                        el.style.color = this.addEditForm.selectFontColor
                        el.style.fontSize = this.addEditForm.selectFontSize
                        el.style.borderWidth = this.addEditForm.selectBorderWidth
                        el.style.borderStyle = this.addEditForm.selectBorderStyle
                        el.style.borderColor = this.addEditForm.selectBorderColor
                        el.style.borderRadius = this.addEditForm.selectBorderRadius
                        el.style.backgroundColor = this.addEditForm.selectBgColor
                    })
                    document.querySelectorAll('.addEdit-block .select .el-form-item__label').forEach(el=>{
                        el.style.lineHeight = this.addEditForm.selectHeight
                        el.style.color = this.addEditForm.selectLableColor
                        el.style.fontSize = this.addEditForm.selectLableFontSize
                    })
                    document.querySelectorAll('.addEdit-block .select .el-select__caret').forEach(el=>{
                        el.style.color = this.addEditForm.selectIconFontColor
                        el.style.fontSize = this.addEditForm.selectIconFontSize
                    })
                    // date
                    document.querySelectorAll('.addEdit-block .date .el-input__inner').forEach(el=>{
                        el.style.height = this.addEditForm.dateHeight
                        el.style.color = this.addEditForm.dateFontColor
                        el.style.fontSize = this.addEditForm.dateFontSize
                        el.style.borderWidth = this.addEditForm.dateBorderWidth
                        el.style.borderStyle = this.addEditForm.dateBorderStyle
                        el.style.borderColor = this.addEditForm.dateBorderColor
                        el.style.borderRadius = this.addEditForm.dateBorderRadius
                        el.style.backgroundColor = this.addEditForm.dateBgColor
                    })
                    document.querySelectorAll('.addEdit-block .date .el-form-item__label').forEach(el=>{
                        el.style.lineHeight = this.addEditForm.dateHeight
                        el.style.color = this.addEditForm.dateLableColor
                        el.style.fontSize = this.addEditForm.dateLableFontSize
                    })
                    document.querySelectorAll('.addEdit-block .date .el-input__icon').forEach(el=>{
                        el.style.color = this.addEditForm.dateIconFontColor
                        el.style.fontSize = this.addEditForm.dateIconFontSize
                        el.style.lineHeight = this.addEditForm.dateHeight
                    })
                    // upload
                    let iconLineHeight = parseInt(this.addEditForm.uploadHeight) - parseInt(this.addEditForm.uploadBorderWidth) * 2 + 'px'
                    document.querySelectorAll('.addEdit-block .upload .el-upload--picture-card').forEach(el=>{
                        el.style.width = this.addEditForm.uploadHeight
                        el.style.height = this.addEditForm.uploadHeight
                        el.style.borderWidth = this.addEditForm.uploadBorderWidth
                        el.style.borderStyle = this.addEditForm.uploadBorderStyle
                        el.style.borderColor = this.addEditForm.uploadBorderColor
                        el.style.borderRadius = this.addEditForm.uploadBorderRadius
                        el.style.backgroundColor = this.addEditForm.uploadBgColor
                    })
                    document.querySelectorAll('.addEdit-block .upload .el-form-item__label').forEach(el=>{
                        el.style.lineHeight = this.addEditForm.uploadHeight
                        el.style.color = this.addEditForm.uploadLableColor
                        el.style.fontSize = this.addEditForm.uploadLableFontSize
                    })
                    document.querySelectorAll('.addEdit-block .upload .el-icon-plus').forEach(el=>{
                        el.style.color = this.addEditForm.uploadIconFontColor
                        el.style.fontSize = this.addEditForm.uploadIconFontSize
                        el.style.lineHeight = iconLineHeight
                        el.style.display = 'block'
                    })
                    // 多文本输入框
                    document.querySelectorAll('.addEdit-block .textarea .el-textarea__inner').forEach(el=>{
                        el.style.height = this.addEditForm.textareaHeight
                        el.style.color = this.addEditForm.textareaFontColor
                        el.style.fontSize = this.addEditForm.textareaFontSize
                        el.style.borderWidth = this.addEditForm.textareaBorderWidth
                        el.style.borderStyle = this.addEditForm.textareaBorderStyle
                        el.style.borderColor = this.addEditForm.textareaBorderColor
                        el.style.borderRadius = this.addEditForm.textareaBorderRadius
                        el.style.backgroundColor = this.addEditForm.textareaBgColor
                    })
                    document.querySelectorAll('.addEdit-block .textarea .el-form-item__label').forEach(el=>{
                        // el.style.lineHeight = this.addEditForm.textareaHeight
                        el.style.color = this.addEditForm.textareaLableColor
                        el.style.fontSize = this.addEditForm.textareaLableFontSize
                    })
                    // 保存
                    document.querySelectorAll('.addEdit-block .btn .btn-success').forEach(el=>{
                        el.style.width = this.addEditForm.btnSaveWidth
                        el.style.height = this.addEditForm.btnSaveHeight
                        el.style.color = this.addEditForm.btnSaveFontColor
                        el.style.fontSize = this.addEditForm.btnSaveFontSize
                        el.style.borderWidth = this.addEditForm.btnSaveBorderWidth
                        el.style.borderStyle = this.addEditForm.btnSaveBorderStyle
                        el.style.borderColor = this.addEditForm.btnSaveBorderColor
                        el.style.borderRadius = this.addEditForm.btnSaveBorderRadius
                        el.style.backgroundColor = this.addEditForm.btnSaveBgColor
                    })
                    // 返回
                    document.querySelectorAll('.addEdit-block .btn .btn-close').forEach(el=>{
                        el.style.width = this.addEditForm.btnCancelWidth
                        el.style.height = this.addEditForm.btnCancelHeight
                        el.style.color = this.addEditForm.btnCancelFontColor
                        el.style.fontSize = this.addEditForm.btnCancelFontSize
                        el.style.borderWidth = this.addEditForm.btnCancelBorderWidth
                        el.style.borderStyle = this.addEditForm.btnCancelBorderStyle
                        el.style.borderColor = this.addEditForm.btnCancelBorderColor
                        el.style.borderRadius = this.addEditForm.btnCancelBorderRadius
                        el.style.backgroundColor = this.addEditForm.btnCancelBgColor
                    })
                })
            },
            addEditUploadStyleChange() {
                this.$nextTick(()=>{
                    document.querySelectorAll('.addEdit-block .upload .el-upload-list--picture-card .el-upload-list__item').forEach(el=>{
                        el.style.width = this.addEditForm.uploadHeight
                        el.style.height = this.addEditForm.uploadHeight
                        el.style.borderWidth = this.addEditForm.uploadBorderWidth
                        el.style.borderStyle = this.addEditForm.uploadBorderStyle
                        el.style.borderColor = this.addEditForm.uploadBorderColor
                        el.style.borderRadius = this.addEditForm.uploadBorderRadius
                        el.style.backgroundColor = this.addEditForm.uploadBgColor
                    })
                })
            },
        }
    };
</script>

