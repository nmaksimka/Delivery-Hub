pluginManagement {
    includeBuild("build-logic")
}

rootProject.name = "DeliveryHub"

listOf(
    "apiContracts",
    "discoveryServer",
    "configServer",
    "apiGateway",
    "orderService",
    "restaurantService",
    "deliveryService",
    "userService"
).filter { file(it).exists() }.forEach { include(it) }
