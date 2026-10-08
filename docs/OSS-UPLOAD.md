# 阿里云 OSS 文件上传模块使用说明

本模块将上传方法封装在 `FileUploadService.upload(MultipartFile)` 中。业务代码注入服务后即可得到普通 HTTPS 链接，并把链接原文保存到已有业务字段，例如医生 `avatarUrl`。前端也可调用 `POST /api/v1/files/upload`，获取相同上传结果。

模块使用 Java 17、OSS Java SDK V1 `3.18.4`、V4 请求签名及单例连接池。SDK 负责对发往 OSS 的请求签名；返回链接不包含临时签名，数据库保存链接时不加密。这里的请求认证签名和数据库链接保存是两件不同的事。

## 1. 文件与职责

```text
backend/src/main/java/com/dental/
├─ config/
│  ├─ OssProperties.java          app.oss 配置绑定、格式校验、访问地址生成
│  └─ OssConfig.java              单例 OSS 客户端、V4 签名、销毁连接池
└─ file/
   ├─ controller/FileUploadController.java   HTTP 上传入口、R<FileUploadVO>
   ├─ service/FileUploadService.java         文件校验、对象命名、流式上传
   └─ vo/FileUploadVO.java                   上传结果
```

已有数据库业务模块继续使用 `controller/`、`service/`、`mapper/`、`entity/`、`dto/`、`vo/` 分层。本次 `file` 是通用文件传输模块，不建立文件表，不直接写业务数据库，因此无需添加 Mapper、数据库实体或 Flyway SQL；HTTP 文件由 Spring 的 `MultipartFile` 接收。

上传成功不代表业务记录已经保存。调用方仍需通过对应业务服务或保存接口，把返回的 `url` 写入业务字段。修改或逻辑删除业务记录不会自动物理删除 OSS 对象。

## 2. 准备 Bucket 与 RAM 凭证

1. 在 OSS 控制台确认 Bucket 名为 `dental-backend`，地域为华北 2（北京）。本机通过外网 Endpoint 上传：`https://oss-cn-beijing.aliyuncs.com`，地域 ID 为 `cn-beijing`。其他地域应同时修改 Endpoint 和 region。
2. 创建或使用有 AccessKey 的 RAM 用户。AccessKey Secret 只在创建时展示；原 Secret 未保存时，创建新的 AccessKey，并把新的一对 ID 和 Secret 填入后端配置。
3. 给 RAM 用户授予本模块所需的上传权限。以下自定义策略只允许写入本模块的对象前缀，示例与默认 `object-prefix` 对应：

```json
{
  "Version": "1",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["oss:PutObject"],
      "Resource": ["acs:oss:*:*:dental-backend/dental/uploads/*"]
    }
  ]
}
```

本方法不创建 Bucket、不列举对象、不读取文件、不设置 ACL，因此不需要为此授予 `AliyunOSSFullAccess`。如果 Bucket Policy 另有限制，应一并确认写入权限。OSS 控制台的 Bucket 名、地域与 RAM 策略配置由使用者完成。

## 3. 静态配置并启用

在 `backend/src/main/resources/application.yml` 中编辑 `app.oss` 的非敏感设置；将真实 AccessKey ID/Secret 写入 Git 忽略的 `backend/src/main/resources/application-local.yml`。首次使用先复制 `application-local.example.yml`。下例的 AccessKey 字符串仅作填写提示，不要提交真实凭证；不使用环境变量或 `${...}` 动态占位符。

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 11MB

app:
  oss:
    enabled: false
    endpoint: https://oss-cn-beijing.aliyuncs.com
    region: cn-beijing
    bucket-name: dental-backend
    access-key-id: '填写自己的AccessKeyId'
    access-key-secret: '填写与上面ID配套的AccessKeySecret'
    public-base-url: ''
    object-prefix: dental/uploads
    max-file-size: 10MB
    allowed-extensions: [jpg, jpeg, png, gif, webp, pdf, doc, docx, xls, xlsx, txt, csv, zip]
```

将非敏感条目合并到 `application.yml` 已有的 `spring` 和 `app` 节点内，不要重复新增同名顶层节点。将 AccessKey ID/Secret 写入本机 `application-local.yml` 的 `app.oss` 节点。凭证填写完整后将 `enabled` 改为 `true`，重新启动后端。本机已按用户提供的凭证启用并完成真实上传验证；关闭时上传接口返回 HTTP 503 / `FILE_STORAGE_NOT_CONFIGURED`。

启用时启动过程会校验 Bucket 名、凭证是否为空、HTTPS Endpoint、region、对象前缀和扩展名配置，不会在启动时访问 OSS。格式校验通过不代表凭证或云端权限正确，真实上传才能确认。

`application-local.yml` 保留本机真实凭据并受 Git 忽略；local 配置会覆盖 `application.yml` 中同名的空值。不要把真实 AccessKey 放进前端、小程序或本说明文档中。

`public-base-url` 留空时，返回链接为：

```text
https://dental-backend.oss-cn-beijing.aliyuncs.com/dental/uploads/2026/10/07/<UUID>.jpg
```

如果已经绑定可访问的 HTTPS 自定义域名，可把 `public-base-url` 改成该域名，例如 `https://files.example.com`。此项只改变返回链接；上传仍使用 `endpoint`。内网上传 Endpoint 对应的默认返回链接会换成外网地址。

## 4. 链接与访问权限

返回值是稳定的普通 URL，不加密，不使用 `generatePresignedUrl`，也不会自动修改 Bucket 或 Object ACL。对象权限继承 Bucket 的设置。

如果 Bucket/Object 为私有，上传可以成功，链接也可以正常存入数据库，但浏览器匿名打开这个无签名链接通常会收到 403。公共读资源才能按普通地址直接匿名查看；返回 URL 本身不会让私有资源变公开。医生头像等准备公开展示的资源，需要使用者在云端决定适当的读权限或访问域名方案。病例、检查报告等患者资料不应因需要存链接就改成公开资源。

本次只提供上传与返回链接，不包含私有文件的授权下载模块。

## 5. HTTP 上传接口

| 项目 | 约定 |
| --- | --- |
| 路径 | `POST /api/v1/files/upload` |
| 认证 | `Authorization: Bearer <登录接口返回的token>`，ADMIN、DOCTOR、PATIENT 均可使用 |
| 请求类型 | `multipart/form-data` |
| 文件字段 | `file`，必填，一个文件 |
| 响应 | `R<FileUploadVO>` |
| 大小 | 单文件最多 10 MiB（10 × 1024 × 1024 字节）；整个 multipart 请求最多 11 MiB |
| 文件名 | 不能为空，剥离客户端目录后最多 255 个字符 |
| 扩展名 | `jpg/jpeg/png/gif/webp/pdf/doc/docx/xls/xlsx/txt/csv/zip`，大小写不敏感 |

文件二进制使用 multipart 的 `file` 部分传输，不包装成 JSON 的 `{ "data": ... }`，也不转换成 Base64。响应与其他接口一致，仍由 `R` 包装。现有前端 JSON 请求工具会自动加入 `data` 外层，因此上传需要独立使用 `FormData` 或 `uni.uploadFile`。

成功响应示例（UUID、日期和 traceId 仅为示例）：

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "url": "https://dental-backend.oss-cn-beijing.aliyuncs.com/dental/uploads/2026/10/07/a9b51f4f28cc4ad38f69c35045a57891.jpg",
    "objectKey": "dental/uploads/2026/10/07/a9b51f4f28cc4ad38f69c35045a57891.jpg",
    "originalFilename": "doctor.jpg",
    "size": 12345,
    "contentType": "image/jpeg"
  },
  "traceId": "示例追踪标识"
}
```

`size` 单位为字节。对象键由日期和 UUID 生成，原文件名只作为响应信息；SDK 请求还设置禁止覆盖。服务根据扩展名设置 Content-Type，非图片对象增加附件下载头。扩展名白名单并不是文件内容鉴别或病毒扫描。

## 6. Vue 管理端调用示例

以下函数接收页面文件选择器得到的 `File`。本机开发可使用现有 Vite 代理下的 `/api/v1` 地址。

```ts
type FileUploadResult = {
  url: string;
  objectKey: string;
  originalFilename: string;
  size: number;
  contentType: string;
};

async function uploadFile(file: File): Promise<FileUploadResult> {
  const token = localStorage.getItem('dental.admin.token') || '';
  const form = new FormData();
  form.append('file', file);
  const response = await fetch('/api/v1/files/upload', {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: form,
  });
  const result = await response.json();
  if (!response.ok || result.code !== 'OK') {
    throw new Error(result.message || '文件上传失败');
  }
  return result.data;
}

// 文件选择完成后：先上传，再使用原有医生保存接口提交表单。
// const uploaded = await uploadFile(selectedFile);
// doctorForm.avatarUrl = uploaded.url;
// await api.put(`/admin/doctors/${doctorId}`, doctorForm);
```

浏览器会生成包含 boundary 的 Content-Type，请不要手动给这个请求设置 `application/json` 或不含 boundary 的 `multipart/form-data`。实际页面应按已有登录处理逻辑处理 401，并展示后端返回的错误消息。

## 7. uni-app / 微信小程序调用示例

下例作为 `miniapp/src/utils/upload.js` 的后续接入参考，复用当前工程导出的 `API_BASE_URL`、`getToken`。`filePath` 来自 `uni.chooseImage` 等文件选择接口，不能传本机服务端文件路径。

```js
import { API_BASE_URL } from './request';
import { getToken } from './session';

export function uploadFile(filePath) {
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: `${API_BASE_URL}/files/upload`,
      filePath,
      name: 'file',
      header: { Authorization: `Bearer ${getToken()}` },
      timeout: 60000,
      success(response) {
        let result;
        try {
          result = JSON.parse(response.data);
        } catch {
          reject(new Error('上传响应格式不正确'));
          return;
        }
        if (response.statusCode >= 200 && response.statusCode < 300
            && result.code === 'OK') {
          resolve(result.data);
        } else {
          const error = new Error(result.message || '文件上传失败');
          error.code = result.code;
          error.status = response.statusCode;
          reject(error);
        }
      },
      fail: () => reject(new Error('文件上传网络请求失败')),
    });
  });
}
```

`uni.uploadFile` 返回的 `response.data` 是字符串，需要 JSON 解析。真机访问后端时，API 地址应使用手机可访问的服务器地址；`127.0.0.1` 只适用于相应本机开发环境。小程序发布前应配置上传后端的 HTTPS 域名白名单。字段与响应约定见 [uni.uploadFile 官方文档](https://uniapp.dcloud.net.cn/api/request/network-file.html)。

## 8. Java 业务层直接复用

通过构造器注入 `FileUploadService`，上传后读取 `url()`：

```java
import com.dental.file.service.FileUploadService;
import com.dental.file.vo.FileUploadVO;
import org.springframework.web.multipart.MultipartFile;

public class YourBusinessService {

    private final FileUploadService fileUploadService;

    public YourBusinessService(FileUploadService fileUploadService) {
        this.fileUploadService = fileUploadService;
    }

    public String uploadAttachment(MultipartFile file) {
        FileUploadVO uploaded = fileUploadService.upload(file);
        return uploaded.url();
    }
}
```

在实际 Spring 业务 Service 中使用上述构造器即可。医生保存流程可把 `uploaded.url()` 放入 `DoctorSaveDTO.avatarUrl`，再沿用原有 service / MyBatis-Plus 保存流程。不要在每次业务调用结束后关闭 OSS 单例；客户端由 Spring 容器管理，容器关闭时统一释放连接池。

## 9. 排查错误

| HTTP 状态 / code | 常见原因 | 处理 |
| --- | --- | --- |
| 401 / `UNAUTHORIZED` | 未登录、token 失效 | 重新登录，传 Bearer token |
| 400 / `INVALID_ARGUMENT` | 缺少 `file`、空文件、无后缀、不支持的扩展名、文件名过长、multipart 格式错误 | 按第 5 节检查请求和文件 |
| 413 / `FILE_TOO_LARGE` | 超过文件或请求大小上限 | 缩小文件；如调整限额，同步调整 `spring.servlet.multipart` 与 `app.oss.max-file-size` |
| 415 / `UNSUPPORTED_MEDIA_TYPE` | 使用 JSON 等不支持的请求类型 | 使用 `multipart/form-data` 上传文件 |
| 503 / `FILE_STORAGE_NOT_CONFIGURED` | `app.oss.enabled=false` 或客户端未配置 | 填好凭证、Endpoint、region、Bucket 并启用，再重启 |
| 502 / `FILE_UPLOAD_FAILED` | OSS 拒绝请求、网络/超时异常或文件读取失败 | 查看后端记录的 OSS errorCode 与 requestId，核对云端配置 |

OSS 的 `AccessDenied` 常指 RAM/Bucket Policy 权限不足；`NoSuchBucket` 需核对 Bucket 名与 Endpoint；签名相关错误需核对同一对 AccessKey、region 与 Endpoint。接口不会把 Secret、完整云端异常或内部配置返回给前端。上传成功后访问 URL 的 403，请先检查读权限，第 4 节说明了私有对象行为。

启用时如果 `app.oss.*` 格式校验报错，按启动日志指出的配置项修改，不要把错误的地域留作示例值。

## 10. 验证边界与官方资料

2026-10-07 已使用 Java 17 和本机 Maven 执行 `verify`：28 项测试全部通过（25 项上传模块测试与 3 项已有公共契约测试），编译和 JAR 打包成功。自动测试验证文件校验、对象键与元数据、客户端生命周期、认证、R 响应和异常转换；真实 SDK 对本机环回 HTTP 服务的上传测试覆盖了 Java 17 的 JAXB、V4 签名、文件正文及成功响应处理，普通 `verify` 不访问云端。

凭证补齐后，通过同一个 `FileUploadService.upload` 方法实际上传了 135 字节生成测试文本（不含患者或个人信息）至北京 `dental-backend`，服务返回成功链接。对象键为 `dental/uploads/2026/10/07/97eeaa1fa95b4afa80a359f3f4a8ba71.txt`，结果记录位于本机忽略目录 `.local/oss-verification/result.json`。未访问或写入业务数据库，未修改 Bucket/Object 权限。

该普通 URL 的匿名 GET 返回 **403**，说明当前读权限不允许匿名读取；这不影响已经成功的上传及保存链接。后续公开头像或附件访问功能需要按第 4 节配置相应读取方案。重新做云端联调时，登录后调用 HTTP 上传接口并核对 OSS 控制台中的文件；是否能匿名打开返回 URL 由云端读权限决定。

本模块使用以下官方资料；页面示例中的地域按本项目北京 Bucket 调整：

- [OSS API 概览](https://help.aliyun.com/zh/oss/developer-reference/list-of-operations-by-function)：用户提供的 API 入口。
- [OSS Java SDK V1](https://help.aliyun.com/zh/oss/developer-reference/oss-java-sdk/)：SDK Maven 坐标、V4、region 与单例客户端。
- [简单上传（Java SDK V1）](https://help.aliyun.com/zh/oss/developer-reference/simple-upload-11)：InputStream、PutObjectRequest 与 putObject。
- [PutObject](https://help.aliyun.com/zh/oss/developer-reference/putobject)：上传权限与禁止同名覆盖。
- [地域和 Endpoint](https://help.aliyun.com/zh/oss/user-guide/regions-and-endpoints)：实际 Bucket 的地域与访问地址。
- [固定文件 URL](https://help.aliyun.com/zh/oss/use-a-fixed-file-url-to-access-a-file)：普通链接格式及私有对象限制。
- [阿里云 OSS Java SDK GitHub 仓库](https://github.com/aliyun/aliyun-oss-java-sdk)：官方实现与示例。
