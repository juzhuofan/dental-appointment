INSERT INTO sys_role(role_code,role_name,description,created_at,updated_at) VALUES
 ('ADMIN','管理员','管理诊所资料与预约',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
 ('DOCTOR','医生','查看本人排班及接诊',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
 ('PATIENT','患者','维护本人资料及预约',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3));
INSERT INTO system_config(config_key,config_value,description,created_at,updated_at) VALUES
 ('clinic.name','微笑口腔诊所（毕业设计演示）','虚构诊所',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
 ('clinic.phone','021-55550000','演示联系电话',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
 ('clinic.address','上海市示例路 100 号（虚构地址）','演示地址',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
 ('clinic.openingHours','周一至周日 09:00–18:00','营业时间',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
 ('clinic.introduction','提供口腔检查、洁牙和牙齿护理预约。本系统使用虚构演示资料。','诊所简介',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
 ('appointment.cancel_before_minutes','120','开始前取消时间限制',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3));
