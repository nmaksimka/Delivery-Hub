pluginManagement {
    includeBuild("build-logic")
}

rootProject.name = "DeliveryHub"

listOf(
    "apiContracts",
    "apiGateway",
    "orderService",
    "restaurantService",
    "deliveryService",
    "userService"
).filter { file(it).exists() }.forEach { include(it) }
