package com.gzzz.toimage.data.template

import com.gzzz.toimage.data.local.PromptTemplateEntity

object BuiltInTemplates {

    fun getAll(): List<PromptTemplateEntity> = listOf(
        // 风景
        template("日落山川", "风景", "A breathtaking mountain landscape at golden hour, warm sunlight painting the peaks, dramatic clouds, photorealistic, 8k"),
        template("海边小镇", "风景", "A charming coastal town with colorful houses along the Mediterranean sea, blue water, clear sky, dreamy atmosphere"),
        template("樱花大道", "风景", "A serene Japanese street lined with cherry blossom trees in full bloom, petals falling, soft pink light, peaceful mood"),
        template("星空银河", "风景", "A stunning night sky filled with stars and the Milky Way galaxy, silhouette of mountains, long exposure photography"),
        template("热带雨林", "风景", "A lush tropical rainforest with vibrant green vegetation, misty atmosphere, sunlight filtering through canopy, exotic birds"),

        // 人物
        template("古风仕女", "人物", "An elegant Chinese ancient beauty in traditional hanfu dress, delicate features, ink painting style, soft colors"),
        template("赛博朋克", "人物", "A cyberpunk character with neon-lit clothing, futuristic city background, rain reflections, cinematic lighting"),
        template("水彩少女", "人物", "A beautiful girl portrait in watercolor style, flowing hair, gentle expression, pastel colors, artistic"),
        template("骑士肖像", "人物", "A noble medieval knight in shining armor, dramatic lighting, oil painting style, detailed metalwork"),

        // 动漫
        template("魔法少女", "动漫", "A magical girl character with sparkling transformation effects, anime style, vibrant colors, dynamic pose"),
        template("机甲战士", "动漫", "A giant mecha robot in battle stance, sci-fi cityscape, dramatic perspective, anime art style, detailed mechanical design"),
        template("和风庭院", "动漫", "A peaceful Japanese garden with a torii gate, anime style, cherry blossoms, koi pond, warm afternoon light"),

        // 写实
        template("咖啡时光", "写实", "A cozy coffee shop interior, warm lighting, latte art, wooden furniture, photorealistic, bokeh background"),
        template("城市夜景", "写实", "A metropolitan city skyline at night, neon lights, reflections on wet streets, cinematic photography"),
        template("美食特写", "写实", "A delicious gourmet dish on a white plate, professional food photography, shallow depth of field, warm lighting"),

        // 抽象
        template("流体艺术", "抽象", "Abstract fluid art with swirling vibrant colors, gold accents, marble texture, luxury aesthetic"),
        template("几何构成", "抽象", "Geometric abstract composition with overlapping shapes, bold colors, Bauhaus inspired, modern art"),
        template("梦境迷雾", "抽象", "Surreal dreamlike landscape with floating objects, ethereal mist, soft pastel palette, Salvador Dali inspired")
    )

    private fun template(name: String, category: String, prompt: String) = PromptTemplateEntity(
        name = name,
        category = category,
        prompt = prompt,
        isBuiltIn = true
    )
}
