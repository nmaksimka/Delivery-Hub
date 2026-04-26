rootProject.name = "DeliveryHub"

val allModules = listOf(
    "apiContracts",
    "apiGateway",
    "orderService",
    "restaurantService",
    "deliveryService",
    "userService"
)

allModules.forEach { module ->
    if(file(module).exists()) {
        include(module)
    }
}