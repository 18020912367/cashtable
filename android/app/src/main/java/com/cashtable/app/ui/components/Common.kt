package com.cashtable.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AssignmentReturn
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.ChildFriendly
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Icecream
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material.icons.filled.LocalPizza
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TakeoutDining
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashtable.app.data.CategoryDto
import com.cashtable.app.util.Formatters

/** 分类图标映射（Material Symbols 名称 → Compose Icon），见 design.md 6.2。 */
private val iconMap: Map<String, ImageVector> = mapOf(
    "三餐" to Icons.Filled.Restaurant,
    "零食" to Icons.Filled.Cookie,
    "衣服" to Icons.Filled.Checkroom,
    "交通" to Icons.Filled.DirectionsBus,
    "旅游" to Icons.Filled.Luggage,
    "孩子" to Icons.Filled.ChildCare,
    "宠物" to Icons.Filled.Pets,
    "水电网燃气" to Icons.Filled.Bolt,
    "日用品" to Icons.Filled.ShoppingBasket,
    "家具家电" to Icons.Filled.Chair,
    "住房" to Icons.Filled.Home,
    "人情往来" to Icons.Filled.Favorite,
    "健身运动" to Icons.Filled.FitnessCenter,
    "医疗" to Icons.Filled.MedicalServices,
    "娱乐" to Icons.Filled.Movie,
    "退款" to Icons.AutoMirrored.Filled.AssignmentReturn,
    "工资" to Icons.Filled.Payments,
)

/** 内置图标库（key → 显示名 → 图标），新增分类时由用户选择，key 存后端 icon 字段。 */
val iconChoices: List<Triple<String, String, ImageVector>> = listOf(
    // 原有 16 个
    Triple("chair", "家具", Icons.Filled.Chair),
    Triple("home", "住房", Icons.Filled.Home),
    Triple("favorite", "人情", Icons.Filled.Favorite),
    Triple("fitness", "健身", Icons.Filled.FitnessCenter),
    Triple("medical", "医疗", Icons.Filled.MedicalServices),
    Triple("movie", "娱乐", Icons.Filled.Movie),
    Triple("shopping", "购物", Icons.Filled.ShoppingCart),
    Triple("school", "教育", Icons.Filled.School),
    Triple("phone", "通讯", Icons.Filled.Phone),
    Triple("gift", "礼物", Icons.Filled.CardGiftcard),
    Triple("sports", "运动", Icons.Filled.SportsBasketball),
    Triple("beauty", "美妆", Icons.Filled.Face),
    Triple("plane", "出行", Icons.Filled.Flight),
    Triple("coffee", "饮品", Icons.Filled.Coffee),
    Triple("book", "书籍", Icons.Filled.Book),
    Triple("pet", "宠物", Icons.Filled.Pets),
    // 银行/理财
    Triple("bank", "银行", Icons.Filled.AccountBalance),
    Triple("card", "信用卡", Icons.Filled.CreditCard),
    Triple("savings", "储蓄", Icons.Filled.Savings),
    Triple("cash", "现金", Icons.Filled.AttachMoney),
    Triple("receipt", "发票", Icons.Filled.ReceiptLong),
    // 餐饮
    Triple("fastfood", "快餐", Icons.Filled.Fastfood),
    Triple("pizza", "披萨", Icons.Filled.LocalPizza),
    Triple("icecream", "冰淇淋", Icons.Filled.Icecream),
    Triple("cake", "蛋糕", Icons.Filled.Cake),
    Triple("takeout", "外卖", Icons.Filled.TakeoutDining),
    Triple("bakery", "烘焙", Icons.Filled.BakeryDining),
    // 出行
    Triple("car", "汽车", Icons.Filled.DirectionsCar),
    Triple("train", "火车", Icons.Filled.Train),
    Triple("taxi", "打车", Icons.Filled.LocalTaxi),
    Triple("walk", "步行", Icons.Filled.DirectionsWalk),
    Triple("gas", "加油", Icons.Filled.LocalGasStation),
    // 购物/生活
    Triple("mall", "商场", Icons.Filled.LocalMall),
    Triple("store", "商店", Icons.Filled.Storefront),
    Triple("light", "照明", Icons.Filled.Lightbulb),
    Triple("kitchen", "厨房", Icons.Filled.Kitchen),
    Triple("sofa", "软装", Icons.Filled.Weekend),
    // 健康/亲子
    Triple("hospital", "医院", Icons.Filled.LocalHospital),
    Triple("med", "药品", Icons.Filled.Medication),
    Triple("spa", "养生", Icons.Filled.Spa),
    Triple("baby", "亲子", Icons.Filled.ChildFriendly),
    Triple("toy", "玩具", Icons.Filled.Toys),
    // 娱乐/数码
    Triple("game", "游戏机", Icons.Filled.Gamepad),
    Triple("esports", "电竞", Icons.Filled.SportsEsports),
    Triple("music", "音乐", Icons.Filled.MusicNote),
    Triple("headphone", "耳机", Icons.Filled.Headphones),
    Triple("camera", "相机", Icons.Filled.CameraAlt),
    Triple("computer", "电脑", Icons.Filled.Computer),
    Triple("devices", "数码", Icons.Filled.Devices),
    // 其他
    Triple("celebrate", "庆祝", Icons.Filled.Celebration),
    Triple("redeem", "礼券", Icons.Filled.Redeem),
    Triple("flower", "鲜花", Icons.Filled.LocalFlorist),
)

/** icon key → ImageVector。 */
val iconKeyMap: Map<String, ImageVector> = iconChoices.associate { it.first to it.third }

fun categoryImage(name: String, kind: String): ImageVector =
    iconMap[name] ?: if (kind == "income") Icons.Filled.AddCircle else Icons.Filled.MoreHoriz

/** 优先使用分类自带 icon key，其次按名称映射。 */
fun categoryImage(category: CategoryDto): ImageVector =
    category.icon.takeIf { it.isNotEmpty() }?.let { iconKeyMap[it] }
        ?: iconMap[category.name]
        ?: if (category.kind == "income") Icons.Filled.AddCircle else Icons.Filled.MoreHoriz

/** 圆角方块底色 + 分类图标（列表/九宫格通用）。 */
@Composable
fun CategoryIcon(
    name: String,
    kind: String,
    size: Dp = 40.dp,
    iconSize: Dp = 24.dp,
    modifier: Modifier = Modifier,
) {
    val color = if (kind == "income") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .size(size)
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = categoryImage(name, kind),
            contentDescription = name,
            tint = color,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** 列表展示用分类：二级分类回退到一级（图标取自一级，名称可另取）。 */
fun displayCategory(cat: CategoryDto?, all: List<CategoryDto>): CategoryDto? {
    if (cat == null) return null
    if (cat.parent_id != null) {
        return all.firstOrNull { it.id == cat.parent_id } ?: cat
    }
    return cat
}

/** 圆角方块底色 + 分类图标（接收分类对象：优先 icon 字段，二级分类回退一级图标）。 */
@Composable
fun CategoryIcon(
    category: CategoryDto?,
    size: Dp = 40.dp,
    iconSize: Dp = 24.dp,
    modifier: Modifier = Modifier,
) {
    val kind = category?.kind ?: "expense"
    val color = if (kind == "income") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .size(size)
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = category?.let { categoryImage(it) }
                ?: categoryImage(category?.name ?: "其他", kind),
            contentDescription = category?.name ?: "其他",
            tint = color,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** 金额文本：收入绿色带 +，支出深灰带 -。 */
@Composable
fun AmountText(
    amount: Double,
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
    modifier: Modifier = Modifier,
) {
    val color = when {
        amount > 0 -> MaterialTheme.colorScheme.secondary
        else -> Color(0xFF333333)
    }
    val sign = if (amount >= 0) "+" else "-"
    Text(
        text = "$sign${Formatters.money(amount)}",
        color = color,
        fontSize = fontSize,
        modifier = modifier,
    )
}
