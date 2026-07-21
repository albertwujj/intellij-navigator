class ProductModel:
    """Product model for the application."""

    def __init__(self, name: str, price: float):
        self.name = name
        self.price = price

    def save(self):
        """Save the product to database."""
        pass


class UserModel:
    """Another UserModel in a different file for testing fileHint."""

    def __init__(self, user_id: int):
        self.user_id = user_id
