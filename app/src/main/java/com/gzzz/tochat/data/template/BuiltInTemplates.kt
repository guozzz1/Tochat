package com.gzzz.tochat.data.template

import com.gzzz.tochat.data.local.PromptTemplateEntity

object BuiltInTemplates {

    fun getAll(): List<PromptTemplateEntity> = listOf(
        // 风景
        template("日落山川", "风景", "生成壮丽的山脉风景，金色时刻的阳光洒在山峰上，戏剧性的云彩，写实风格，8K画质"),
        template("海边小镇", "风景", "生成迷人的沿海小镇，地中海沿岸的彩色房屋，蓝色海水，晴朗天空，梦幻氛围"),
        template("樱花大道", "风景", "生成宁静的日本街道，两旁樱花盛开，花瓣飘落，柔和的粉色光线，宁静氛围"),
        template("星空银河", "风景", "生成震撼的夜空，繁星点点，银河清晰可见，山脉剪影，长曝光摄影风格"),
        template("热带雨林", "风景", "生成郁郁葱葱的热带雨林，翠绿植被，薄雾氛围，阳光穿透树冠，珍稀鸟类"),

        // 人物
        template("古风仕女", "人物", "生成优雅的中国古代美女，穿着传统汉服，精致面容，水墨画风格，淡雅色彩"),
        template("赛博朋克", "人物", "生成赛博朋克风格角色，霓虹灯光服装，未来城市背景，雨中倒影，电影感光影"),
        template("水彩少女", "人物", "生成美丽的少女肖像，水彩画风格，飘逸长发，温柔表情，柔和色彩，艺术感"),
        template("骑士肖像", "人物", "生成高贵的中世纪骑士，闪亮盔甲，戏剧性光影，油画风格，精致金属细节"),

        // 动漫
        template("魔法少女", "动漫", "生成魔法少女角色，闪亮变身效果，动漫风格，鲜艳色彩，动感姿势"),
        template("机甲战士", "动漫", "生成巨型机甲机器人战斗姿态，科幻城市景观，戏剧性透视，动漫画风，精致机械设计"),
        template("和风庭院", "动漫", "生成宁静的日式庭院，鸟居门，动漫风格，樱花盛开，锦鲤池塘，温暖午后阳光"),

        // 写实
        template("咖啡时光", "写实", "生成温馨的咖啡馆 interior，温暖灯光，拉花艺术，木质家具，写实摄影，背景虚化"),
        template("城市夜景", "写实", "生成大都市夜景天际线，霓虹灯光，湿漉漉街道的倒影，电影摄影风格"),
        template("美食特写", "写实", "生成精致美食摆在白色餐盘上，专业美食摄影，浅景深，温暖灯光"),

        // 抽象
        template("流体艺术", "抽象", "生成抽象流体艺术，旋转的鲜艳色彩，金色点缀，大理石纹理，奢华美学"),
        template("几何构成", "抽象", "生成几何抽象构图，重叠的形状，大胆色彩，包豪斯风格，现代艺术"),
        template("梦境迷雾", "抽象", "生成超现实梦幻风景，漂浮物体，空灵薄雾，柔和粉彩调色，达利风格")
    )

    private fun template(name: String, category: String, prompt: String) = PromptTemplateEntity(
        name = name,
        category = category,
        prompt = prompt,
        isBuiltIn = true
    )
}
