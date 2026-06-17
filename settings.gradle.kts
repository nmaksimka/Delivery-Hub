pluginManagement {
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "DeliveryHub"

include(
    "apiContracts",
    "apiGateway",
    "orderService",
    "restaurantService",
    "deliveryService",
    "userService"
)
