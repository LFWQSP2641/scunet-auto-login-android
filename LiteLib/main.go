package litelib

import (
	"context"
	"encoding/json"

	scu "github.com/LFWQSP2641/scunet-auto-login/pkg/schools/scu/auth"
)

// Login 执行 SCU 网络认证登录
// username: 用户名
// password: 密码
// extra: JSON 格式的额外信息,如 {"key": "value"}
// 返回: 登录结果消息
func Login(username, password, extra string) string {
	cxt := context.Background()
	auth := scu.NewSCUAuthenticator()

	// extra json to map[string]string
	var extraMap map[string]string
	if err := json.Unmarshal([]byte(extra), &extraMap); err != nil {
		return "解析额外信息失败: " + err.Error()
	}

	if err := auth.Login(cxt, username, password, extraMap); err != nil {
		return "登录失败: " + err.Error()
	}
	return "登录成功"
}

// Logout 执行 SCU 网络认证登录
// username: 用户名
// password: 密码
// extra: JSON 格式的额外信息,如 {"key": "value"}
// 返回: 登录结果消息
func Logout(username, password, extra string) string {
	cxt := context.Background()
	auth := scu.NewSCUAuthenticator()

	// extra json to map[string]string
	var extraMap map[string]string
	if err := json.Unmarshal([]byte(extra), &extraMap); err != nil {
		return "解析额外信息失败: " + err.Error()
	}

	if err := auth.Logout(cxt, username, password, extraMap); err != nil {
		return "登录失败: " + err.Error()
	}
	return "登录成功"
}
