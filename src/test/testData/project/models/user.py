class UserModel:
    """User model for the application."""

    def __init__(self, name: str):
        self.name = name

    def save(self):
        """Save the user to database."""
        pass

    def delete(self):
        """Delete the user."""
        pass


def create_user(name: str) -> UserModel:
    """Factory function to create a user."""
    return UserModel(name)
